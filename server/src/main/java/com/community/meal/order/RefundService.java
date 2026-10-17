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
public class RefundService {
  private final Db db;
  private final Wallets wallets;
  private final Access access;
  private final OrderQueryService orders;
  private final com.community.meal.payment.PaymentGateway gateway;

  @org.springframework.beans.factory.annotation.Autowired
  public RefundService(
      Db db,
      Access access,
      OrderQueryService orders,
      com.community.meal.payment.PaymentGateway gateway, Wallets wallets) {
    this.db = db;
    this.access = access;
    this.orders = orders;
    this.gateway = gateway;
    this.wallets = wallets;
  }

  public RefundService(Db db,Access access,OrderQueryService orders,com.community.meal.payment.PaymentGateway gateway){this(db,access,orders,gateway,null);}

  public Map<String, Object> cancel(Actor p, long id, Map<String, Object> input) {
    access.permit(p, "orders:cancel", "FAMILY", "COMMUNITY");
    var order = orders.raw(id, true);
    orders.authorize(p, order);
    String state = str(order.get("status"));
    if (state.equals("CANCELLED")) return orders.view(order);
    if (state.equals("PENDING_PAYMENT")) {
      db.exec("UPDATE meal_order SET status='CANCELLED' WHERE id=?", id);
      db.audit(p, "ORDER_CANCEL", "ORDER", id);
      return orders.view(orders.raw(id, false));
    }
    if (state.equals("CANCEL_REQUESTED")) {
      var refund = db.one("SELECT id FROM refund_record WHERE order_id=?", id);
      return map("id", id, "refundId", refund.get("id"), "status", state);
    }
    rule(state.equals("PAID"), "仅未备餐的已支付订单可以申请取消");
    String reason = text(input, "reason", 500, false);
    var previous = db.optional("SELECT * FROM refund_record WHERE order_id=?", id);
    long refund;
    if (previous == null)
      refund =
          db.insert(
              "INSERT INTO refund_record(order_id,reason,amount,idempotency_key)VALUES(?,?,?,?)",
              id,
              reason,
              order.get("payableAmount"),
              "RF" + UUID.randomUUID());
    else {
      refund = num(previous.get("id"));
      rule(str(previous.get("status")).equals("REJECTED"), "已有退款正在处理");
      db.exec(
          "UPDATE refund_record SET"
              + " status='PENDING_REVIEW',reason=?,review_note=NULL,reviewed_by=NULL WHERE id=?",
          reason,
          refund);
    }
    db.exec("UPDATE meal_order SET status='CANCEL_REQUESTED' WHERE id=?", id);
    db.audit(p, "REFUND_REQUEST", "ORDER", id);
    return map("id", id, "refundId", refund, "status", "CANCEL_REQUESTED");
  }

  public Map<String, Object> refunds(Actor p, int page, int size, String keyword) {return refunds(p,page,size,keyword,"");}
  public Map<String,Object> refunds(Actor p,int page,int size,String keyword,String status) {return refunds(p,page,size,keyword,status,null);}
  public Map<String,Object> refunds(Actor p,int page,int size,String keyword,String status,Long siteId) {
    access.role(p, "COMMUNITY");
    return db.page(
        "SELECT r.*,o.site_id,o.order_no,e.name elder_name FROM refund_record r JOIN meal_order o"
            + " ON o.id=r.order_id JOIN elder e ON e.id=o.elder_id WHERE o.site_id IN(SELECT"
            + " site_id FROM sys_user_site WHERE user_id=?) AND (o.order_no LIKE ? OR e.name LIKE ?) AND (?='' OR r.status=?) AND (? IS NULL OR o.site_id=?) ORDER BY r.id DESC",
        Arrays.asList(p.userId(),"%"+keyword.trim()+"%","%"+keyword.trim()+"%",status,status,siteId,siteId),
        page,
        size);
  }

  public Map<String, Object> refund(Actor p, long id) {
    access.role(p, "COMMUNITY");
    var refund =
        db.one(
            "SELECT r.*,o.site_id,o.order_no FROM refund_record r JOIN meal_order o ON"
                + " o.id=r.order_id WHERE r.id=?",
            id);
    access.site(p, num(refund.get("siteId")));
    return refund;
  }

  public Map<String, Object> reviewRefund(
      Actor p, long id, String action, Map<String, Object> input) {
    access.permit(p, "refunds:review", "COMMUNITY");
    var initial = refund(p, id);
    var order = orders.raw(num(initial.get("orderId")), true);
    var refund = db.one("SELECT * FROM refund_record WHERE id=? FOR UPDATE", id);
    String state = str(refund.get("status"));
    if (state.equals("REFUNDED")) return refund;
    boolean retry = action.equals("retry");
    rule(retry ? state.equals("REFUND_FAILED") : state.equals("PENDING_REVIEW"), "退款状态不允许此操作");
    String note = text(input, "note", 500, !retry);
    if (action.equals("reject")) {
      db.exec(
          "UPDATE refund_record SET"
              + " status='REJECTED',reviewed_by=?,review_note=?,reviewed_at=UTC_TIMESTAMP() WHERE"
              + " id=?",
          p.userId(),
          note,
          id);
      db.exec("UPDATE meal_order SET status='PAID' WHERE id=?", order.get("id"));
    } else {
      rule(
          decimal(refund.get("amount")).compareTo(decimal(order.get("payableAmount"))) == 0,
          "退款金额与实付不一致");
      var paid =
          db.one(
              "SELECT * FROM payment_record WHERE order_id=? AND status='SUCCESS' ORDER BY id LIMIT"
                  + " 1",
              order.get("id"));
      db.exec(
          "UPDATE refund_record SET"
              + " status='APPROVED',reviewed_by=?,review_note=?,reviewed_at=UTC_TIMESTAMP() WHERE"
              + " id=?",
          p.userId(),
          note,
          id);
      db.exec("UPDATE meal_order SET status='REFUND_PENDING' WHERE id=?", order.get("id"));
      try {
        String transaction =
            str(paid.get("channel")).equals("WALLET")
                ? wallets.refund(num(paid.get("payerUserId")),num(paid.get("id")),id,num(order.get("id")),decimal(refund.get("amount")))
                : gateway.refund(
                str(paid.get("channel")),
                str(refund.get("idempotencyKey")),
                decimal(refund.get("amount")));
        db.exec(
            "UPDATE refund_record SET"
                + " status='REFUNDED',gateway_refund_no=?,refunded_at=UTC_TIMESTAMP() WHERE id=?",
            transaction,
            id);
        db.exec("UPDATE meal_order SET status='REFUNDED' WHERE id=?", order.get("id"));
        db.exec(
            "INSERT IGNORE INTO subsidy_ledger(order_id,amount,entry_type)VALUES(?,?,'REVERSAL')",
            order.get("id"),
            decimal(order.get("subsidyAmount")).negate());
      } catch (IllegalStateException e) {
        db.exec("UPDATE refund_record SET status='REFUND_FAILED' WHERE id=?", id);
      }
    }
    db.audit(p, "REFUND_" + action.toUpperCase(Locale.ROOT), "REFUND", id);
    return refund(p, id);
  }
}
