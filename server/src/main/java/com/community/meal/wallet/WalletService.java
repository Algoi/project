package com.community.meal.wallet;

import static com.community.meal.common.Values.*;
import com.community.meal.auth.Access;
import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.math.*;
import java.util.*;
import org.springframework.core.env.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @Transactional
public class WalletService implements Wallets {
  private final Db db;private final Access access;private final Environment environment;
  public WalletService(Db db,Access access,Environment environment){this.db=db;this.access=access;this.environment=environment;}
  private void ensure(long user){db.exec("INSERT INTO wallet_account(user_id)VALUES(?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)",user);}
  private boolean mock(){return environment.acceptsProfiles(Profiles.of("(dev | test) & !prod"));}
  public Map<String,Object> summary(Actor actor){
    access.role(actor,"FAMILY");ensure(actor.userId());
    var result=db.one("SELECT balance,updated_at FROM wallet_account WHERE user_id=?",actor.userId());
    result.put("canTopup",mock());return result;
  }
  public Map<String,Object> ledger(Actor actor,int page,int size){
    access.role(actor,"FAMILY");return db.page("SELECT w.id,w.entry_type,w.amount,w.balance_after,w.reference_key,w.order_id,w.created_at,o.order_no FROM wallet_ledger w LEFT JOIN meal_order o ON o.id=w.order_id WHERE w.user_id=? ORDER BY w.id DESC",List.of(actor.userId()),page,size);
  }
  public Map<String,Object> topup(Actor actor,Map<String,Object> input){
    access.role(actor,"FAMILY");rule(mock(),"充值渠道尚未配置");fields(input,"amount","requestId");
    BigDecimal amount=decimal(input.get("amount"));
    try{amount=amount.setScale(2,RoundingMode.UNNECESSARY);}catch(ArithmeticException e){throw ApiException.invalid("金额最多两位小数");}
    if(amount.signum()<=0||amount.compareTo(new BigDecimal("10000"))>0)throw ApiException.invalid("充值金额须大于零且不超过 10000 元");
    String request=text(input,"requestId",80,true);
    db.exec("INSERT IGNORE INTO wallet_topup(user_id,amount,request_id)VALUES(?,?,?)",actor.userId(),amount,request);
    var row=db.one("SELECT * FROM wallet_topup WHERE user_id=? AND request_id=? FOR UPDATE",actor.userId(),request);
    if(decimal(row.get("amount")).compareTo(amount)!=0)throw new ApiException(org.springframework.http.HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","相同请求不能使用不同充值金额");
    return row;
  }
  public Map<String,Object> confirm(Actor actor,long id){
    access.role(actor,"FAMILY");rule(mock(),"模拟充值不可用");
    var row=db.one("SELECT * FROM wallet_topup WHERE id=? FOR UPDATE",id);
    if(num(row.get("userId"))!=actor.userId())throw ApiException.forbidden();
    if(!str(row.get("status")).equals("SUCCESS")){
      change(actor.userId(),decimal(row.get("amount")),"TOPUP","T:"+id,null);
      db.exec("UPDATE wallet_topup SET status='SUCCESS',confirmed_at=UTC_TIMESTAMP() WHERE id=?",id);
      db.audit(actor,"WALLET_TOPUP","TOPUP",id);
    }
    return summary(actor);
  }
  @Override @Transactional(propagation=Propagation.MANDATORY)
  public void debit(Actor payer,long payment,long order,BigDecimal amount){
    access.role(payer,"FAMILY");if(amount.signum()<0)throw ApiException.invalid("支付金额不正确");
    change(payer.userId(),amount.negate(),"PAYMENT","P:"+payment,order);
  }
  @Override @Transactional(propagation=Propagation.MANDATORY)
  public String refund(long payer,long payment,long refund,long order,BigDecimal amount){
    var paid=db.one("SELECT amount FROM wallet_ledger WHERE user_id=? AND reference_key=?",payer,"P:"+payment);
    rule(decimal(paid.get("amount")).negate().compareTo(amount)==0,"退款金额与钱包原付款不一致");
    change(payer,amount,"REFUND","R:"+refund,order);return "WALLET_REFUND_"+refund;
  }
  private void change(long user,BigDecimal amount,String type,String reference,Long order){
    ensure(user);var account=db.one("SELECT balance FROM wallet_account WHERE user_id=? FOR UPDATE",user);
    var old=db.optional("SELECT amount FROM wallet_ledger WHERE user_id=? AND reference_key=? FOR UPDATE",user,reference);
    if(old!=null){rule(decimal(old.get("amount")).compareTo(amount)==0,"流水金额不一致");return;}
    BigDecimal balance=decimal(account.get("balance")).add(amount);
    rule(balance.signum()>=0,"钱包余额不足，请先充值或选择其他支付方式");
    rule(balance.compareTo(new BigDecimal("9999999999.99"))<=0,"钱包余额超出上限");
    db.exec("UPDATE wallet_account SET balance=? WHERE user_id=?",balance,user);
    db.exec("INSERT INTO wallet_ledger(user_id,entry_type,amount,balance_after,reference_key,order_id)VALUES(?,?,?,?,?,?)",user,type,amount,balance,reference,order);
  }
}
