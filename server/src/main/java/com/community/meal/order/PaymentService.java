package com.community.meal.order;

import static com.community.meal.common.Values.*;

import com.community.meal.auth.Access;
import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentService {
  private final Db db;
  private final Access access;
  private final OrderQueryService orders;
  private final Crypto crypto;
  private final Wallets wallets;
  private final com.community.meal.payment.PaymentGateway gateway;

  public PaymentService(
      Db db,
      Access access,
      OrderQueryService orders,
      Crypto crypto,
      com.community.meal.payment.PaymentGateway gateway, Wallets wallets) {
    this.db = db;
    this.access = access;
    this.orders = orders;
    this.crypto = crypto;
    this.gateway = gateway;
    this.wallets = wallets;
  }

  public List<String> channels(Actor actor) {
    var result=new ArrayList<>(gateway.channels());if(actor.hasRole("FAMILY"))result.add("WALLET");return result;
  }

  public Map<String,Object> payment(Actor actor,long id,String channel){
    access.role(actor,"FAMILY","COMMUNITY");var order=orders.raw(id,true);orders.authorize(actor,order);
    boolean wallet=channel.equals("WALLET");
    if(wallet){
      access.role(actor,"FAMILY");
      var successful=db.optional("SELECT * FROM payment_record WHERE order_id=? AND channel='WALLET' AND payer_user_id=? AND status='SUCCESS'",id,actor.userId());
      if(successful!=null)return successful;
    }
    rule(channels(actor).contains(channel),"支付渠道尚未配置或不可用");
    rule(str(order.get("status")).equals("PENDING_PAYMENT"),"订单不可重复发起支付");
    rule(Instant.parse(str(order.get("paymentExpiresAt"))).isAfter(Instant.now()),"待支付订单已超时");
    var row=db.optional("SELECT * FROM payment_record WHERE order_id=? AND channel=? AND status='PENDING' AND payer_user_id=? ORDER BY id DESC LIMIT 1",id,channel,actor.userId());
    if(row==null){long payment=db.insert("INSERT INTO payment_record(order_id,channel,payment_no,amount,payer_user_id)VALUES(?,?,?,?,?)",id,channel,"PAY"+UUID.randomUUID().toString().replace("-",""),order.get("payableAmount"),actor.userId());row=db.one("SELECT * FROM payment_record WHERE id=?",payment);db.audit(actor,"PAYMENT_CREATE","ORDER",id);}
    if(wallet){
      long payment=num(row.get("id"));wallets.debit(actor,payment,id,decimal(order.get("payableAmount")));
      db.exec("UPDATE payment_record SET status='SUCCESS',gateway_transaction_no=?,paid_at=UTC_TIMESTAMP() WHERE id=?","WALLET_PAY_"+payment,payment);
      complete(order);db.audit(actor,"WALLET_PAYMENT_SUCCESS","ORDER",id);row=db.one("SELECT * FROM payment_record WHERE id=?",payment);
    }
    return row;
  }

  public List<Map<String, Object>> payments(Actor p, long id) {
    orders.get(p, id);
    return db.list("SELECT * FROM payment_record WHERE order_id=? ORDER BY id DESC", id);
  }

  public Map<String, Object> simulate(Actor p, long payment) {
    rule(gateway.simulated(), "模拟支付不可用");
    var paymentRow = db.one("SELECT * FROM payment_record WHERE id=?", payment);
    var notice =
        gateway.mockNotice(
            str(paymentRow.get("channel")),
            str(paymentRow.get("paymentNo")),
            decimal(paymentRow.get("amount")));
    rule(gateway.verify(notice), "支付通知签名无效");
    var order = orders.raw(num(paymentRow.get("orderId")), true);
    paymentRow = db.one("SELECT * FROM payment_record WHERE id=? FOR UPDATE", payment);
    orders.authorize(p, order);
    access.role(p, "FAMILY", "COMMUNITY");
    if (str(paymentRow.get("status")).equals("SUCCESS")) return orders.view(order);
    rule(str(order.get("status")).equals("PENDING_PAYMENT"), "订单状态不允许支付");
    rule(Instant.parse(str(order.get("paymentExpiresAt"))).isAfter(Instant.now()), "订单支付已超时");
    rule(
        notice.paymentNo().equals(str(paymentRow.get("paymentNo")))
            && notice.channel().equals(str(paymentRow.get("channel")))
            && notice.amount().compareTo(decimal(order.get("payableAmount"))) == 0,
        "支付通知订单、渠道或金额不匹配");
    db.exec(
        "UPDATE payment_record SET"
            + " status='SUCCESS',gateway_transaction_no=?,paid_at=UTC_TIMESTAMP() WHERE id=? AND"
            + " status='PENDING'",
        notice.transactionNo(),
        payment);
    complete(order);
    db.audit(p, "MOCK_PAYMENT_SUCCESS", "ORDER", order.get("id"));
    return orders.view(orders.raw(num(order.get("id")), false));
  }
  private void complete(Map<String,Object> order){
    String voucher =
        UUID.randomUUID().toString().replace("-", "")
            + UUID.randomUUID().toString().substring(0, 8);
    db.exec(
        "UPDATE meal_order SET status='PAID',pickup_code_hash=?,pickup_cipher=? WHERE id=?",
        Hashes.sha256(voucher),
        crypto.encrypt(voucher),
        order.get("id"));
    db.exec(
        "INSERT IGNORE INTO subsidy_ledger(order_id,amount,entry_type)VALUES(?,?,'GRANT')",
        order.get("id"),
        order.get("subsidyAmount"));
  }
}
