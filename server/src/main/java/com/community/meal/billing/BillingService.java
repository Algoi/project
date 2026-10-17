package com.community.meal.billing;
import com.community.meal.auth.Access;
import com.community.meal.common.*;
import com.community.meal.contracts.Actor;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
@Service public class BillingService {
 private final Db db;private final Access access;public BillingService(Db db,Access access){this.db=db;this.access=access;}
 public Map<String,Object> statement(Actor actor,String from,String to,String keyword,int page,int size){
  access.role(actor,"FAMILY");LocalDate today=LocalDate.now(BusinessTime.ZONE),start=from.isBlank()?today.withDayOfMonth(1):LocalDate.parse(from),end=to.isBlank()?today:LocalDate.parse(to);
  if(end.isBefore(start)||java.time.temporal.ChronoUnit.DAYS.between(start,end)>366)throw ApiException.invalid("账单日期范围须在一年内");
  String entries="SELECT 'PAYMENT' entry_type,p.id reference_id,o.id order_id,o.order_no,e.name elder_name,p.paid_at event_at,p.amount,o.original_amount,o.subsidy_amount,p.channel FROM payment_record p JOIN meal_order o ON o.id=p.order_id JOIN elder e ON e.id=o.elder_id WHERE p.status='SUCCESS' AND COALESCE(p.payer_user_id,o.buyer_user_id)=? UNION ALL SELECT 'REFUND',r.id,o.id,o.order_no,e.name,r.refunded_at,r.amount,o.original_amount,o.subsidy_amount,p.channel FROM refund_record r JOIN meal_order o ON o.id=r.order_id JOIN elder e ON e.id=o.elder_id JOIN payment_record p ON p.order_id=o.id AND p.status='SUCCESS' WHERE r.status='REFUNDED' AND COALESCE(p.payer_user_id,o.buyer_user_id)=?";
  String filtered=" FROM ("+entries+") entries WHERE event_at>=? AND event_at<? AND (order_no LIKE ? OR elder_name LIKE ?)";
  List<Object> args=List.of(actor.userId(),actor.userId(),java.sql.Timestamp.from(start.atStartOfDay(BusinessTime.ZONE).toInstant()),java.sql.Timestamp.from(end.plusDays(1).atStartOfDay(BusinessTime.ZONE).toInstant()),"%"+keyword.trim()+"%","%"+keyword.trim()+"%");
  var result=db.page("SELECT *"+filtered+" ORDER BY event_at DESC,entry_type,reference_id DESC",args,page,size);
  var summary=db.one("SELECT COALESCE(SUM(IF(entry_type='PAYMENT',amount,0)),0.00) paid_amount,COALESCE(SUM(IF(entry_type='REFUND',amount,0)),0.00) refund_amount,COALESCE(SUM(IF(entry_type='PAYMENT',amount,-amount)),0.00) net_expense"+filtered,args.toArray());
  result.put("summary",summary);result.put("dateFrom",start.toString());result.put("dateTo",end.toString());return result;
 }
}
