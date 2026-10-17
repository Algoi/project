package com.community.meal.integration;
import com.community.meal.common.*;import com.community.meal.contracts.*;import java.util.*;
import org.springframework.context.event.EventListener;import org.springframework.stereotype.Component;
/** Cross-domain read adapter; writes are delegated to the public inbox contract, in the source transaction. */
@Component
public class BusinessNotificationRouter {
 private final Db db;private final Inbox inbox;public BusinessNotificationRouter(Db db,Inbox inbox){this.db=db;this.inbox=inbox;}
 @EventListener public void on(BusinessEvent e){
  if(e.action().equals("NOTICE_PUBLISH")){
   var n=db.one("SELECT * FROM notice WHERE id=?",e.resourceId());
   if(n.get("expireAt")!=null&&!java.time.Instant.parse(Values.str(n.get("expireAt"))).isAfter(java.time.Instant.now()))return;
   var roles=Values.strings(n.get("targetRoles"));var sites=Values.ids(n.get("targetSites"));
   for(var u:db.list("SELECT id FROM sys_user WHERE status='ACTIVE'")){
    long user=Values.num(u.get("id"));var ownRoles=db.list("SELECT r.code FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=?",user).stream().map(r->Values.str(r.get("code"))).toList();
    boolean admin=ownRoles.contains("ADMIN"),roleMatch=roles.isEmpty()||ownRoles.stream().anyMatch(roles::contains);
    boolean siteMatch=sites.isEmpty()||sites.stream().anyMatch(site->db.count("SELECT COUNT(*) FROM sys_user_site WHERE user_id=? AND site_id=?",user,site)>0||ownRoles.contains("FAMILY")&&db.count("SELECT COUNT(*) FROM family_binding b JOIN elder el ON el.id=b.elder_id WHERE b.family_user_id=? AND b.status='APPROVED' AND el.site_id=?",user,site)>0);
    if(admin||roleMatch&&siteMatch){String role=ownRoles.stream().filter(r->roles.isEmpty()||roles.contains(r)).findFirst().orElse(ownRoles.isEmpty()?"":ownRoles.get(0));if(!role.isEmpty())send(e,user,"新公告："+n.get("title"),"请前往公告页面查看详情。","/"+role.toLowerCase()+"/notices");}
   }
  }else if(e.action().equals("BINDING_REVIEW")||e.action().equals("BINDING_REVOKE")){
   var b=db.one("SELECT family_user_id,status FROM family_binding WHERE id=?",e.resourceId());send(e,Values.num(b.get("familyUserId")),"家属绑定状态更新","绑定状态："+b.get("status"),"/family/elders");
  }else if(e.action().equals("SUBSIDY_REVIEW")){
   var a=db.one("SELECT applicant_user_id,status FROM subsidy_application WHERE id=?",e.resourceId());send(e,Values.num(a.get("applicantUserId")),"补贴申请审核结果","申请状态："+a.get("status"),"/family/elders");
  }else if(Set.of("MOCK_PAYMENT_SUCCESS","WALLET_PAYMENT_SUCCESS","ORDER_READY","ORDER_CANCEL","DELIVERY_STATUS","REFUND_APPROVE","REFUND_REJECT","REFUND_RETRY").contains(e.action())){
   long id=e.resource().equals("REFUND")?db.count("SELECT order_id FROM refund_record WHERE id=?",e.resourceId()):Long.parseLong(e.resourceId());
   var o=db.one("SELECT buyer_user_id,proxy_operator_id,elder_id,order_no,status FROM meal_order WHERE id=?",id);
   Set<Long> users=new HashSet<>();
   for(var u:db.list("SELECT DISTINCT b.family_user_id FROM family_binding b JOIN sys_user_role ur ON ur.user_id=b.family_user_id JOIN sys_role r ON r.id=ur.role_id WHERE b.elder_id=? AND b.status='APPROVED' AND r.code='FAMILY' AND (? IS NOT NULL OR b.family_user_id=?)",o.get("elderId"),o.get("proxyOperatorId"),o.get("buyerUserId")))users.add(Values.num(u.get("familyUserId")));
   for(var u:db.list("SELECT payer_user_id FROM payment_record WHERE order_id=? AND status='SUCCESS'",id))if(u.get("payerUserId")!=null)users.add(Values.num(u.get("payerUserId")));
   for(long user:users){boolean linked=db.count("SELECT COUNT(*) FROM family_binding WHERE family_user_id=? AND elder_id=? AND status='APPROVED'",user,o.get("elderId"))>0;send(e,user,"订单进度更新","订单 "+o.get("orderNo")+"，当前状态："+o.get("status"),linked?"/family/orders?orderId="+id:"/family/payments");}
  }else if(Set.of("SITE_CALENDAR","SITE_STATUS").contains(e.action())){
   for(var u:db.list("SELECT DISTINCT b.family_user_id FROM family_binding b JOIN meal_order o ON o.elder_id=b.elder_id JOIN service_site s ON s.id=o.site_id WHERE b.status='APPROVED' AND o.site_id=? AND (o.proxy_operator_id IS NOT NULL OR o.buyer_user_id=b.family_user_id) AND o.status IN('PENDING_PAYMENT','PAID','PREPARING','READY') AND o.meal_date>=? AND (s.business_status<>'OPEN' OR EXISTS(SELECT 1 FROM site_closed_day c WHERE c.site_id=o.site_id AND c.closed_date=o.meal_date AND c.meal_type=o.meal_type))",e.resourceId(),java.time.LocalDate.now(BusinessTime.ZONE)))send(e,Values.num(u.get("familyUserId")),"供餐安排调整涉及您的订单","服务网点调整了供餐安排，请查看未履约订单并联系社区确认处理。","/family/orders");
  }else if(e.action().equals("TICKET_REPLY")){
   var t=db.one("SELECT created_by FROM feedback_ticket WHERE id=?",e.resourceId());long user=Values.num(t.get("createdBy"));
   if(user!=e.actorId())send(e,user,"反馈工单收到回复","工作人员已回复，请进入工单查看。","/family/tickets");
  }
 }
 private void send(BusinessEvent e,long user,String title,String body,String path){
  // A recipient may have lost its family role; always keep a usable, authorized destination.
  if(path.startsWith("/family")&&db.count("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.code='FAMILY'",user)==0)path="/";
  if(db.count("SELECT COUNT(*) FROM sys_user_permission WHERE user_id=? AND permission_code=?",user,"menu:"+(path.contains("?")?path.substring(0,path.indexOf('?')):path))>0)path="/";
  inbox.send(user,"audit:"+e.id(),title,body,path);
 }
}
