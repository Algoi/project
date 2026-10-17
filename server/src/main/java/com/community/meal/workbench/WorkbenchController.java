package com.community.meal.workbench;
import com.community.meal.auth.Access;import com.community.meal.auth.AuthPrincipal;import com.community.meal.common.*;import java.util.*;
import org.springframework.web.bind.annotation.*;import org.springframework.security.core.annotation.AuthenticationPrincipal;
@RestController @RequestMapping("/api/v1/workbench")
public class WorkbenchController {
 private final Db db;private final Access access;public WorkbenchController(Db db,Access access){this.db=db;this.access=access;}
 @GetMapping public ApiResponse<?> get(@AuthenticationPrincipal AuthPrincipal p,@RequestParam String role,@RequestParam(required=false)Long siteId){
  if(!p.hasRole(role))throw ApiException.forbidden();if(siteId!=null)access.site(p,siteId);
  List<Map<String,Object>> tasks=new ArrayList<>();String prefix="/"+role.toLowerCase(),selection=siteId==null?"":"&siteId="+siteId;
  if(role.equals("FAMILY")){
   String where=" FROM meal_order o WHERE o.elder_id IN(SELECT elder_id FROM family_binding WHERE family_user_id=? AND status='APPROVED') AND (o.buyer_user_id=? OR o.proxy_operator_id IS NOT NULL) AND o.status=?";
   task(p,tasks,"待付款",db.count("SELECT COUNT(*)"+where,p.userId(),p.userId(),"PENDING_PAYMENT"),prefix+"/orders?status=PENDING_PAYMENT");
   task(p,tasks,"待取餐或配送",db.count("SELECT COUNT(*)"+where,p.userId(),p.userId(),"READY"),prefix+"/orders?status=READY");
  } else if(role.equals("CANTEEN")){
   for(String status:List.of("PAID","PREPARING"))task(p,tasks,status.equals("PAID")?"待备餐":"备餐中",db.count("SELECT COUNT(*) FROM meal_order WHERE site_id IN(SELECT site_id FROM sys_user_site WHERE user_id=?) AND (? IS NULL OR site_id=?) AND status=?",p.userId(),siteId,siteId,status),prefix+"/orders?status="+status);
   String today=java.time.LocalDate.now(BusinessTime.ZONE).toString();
   task(p,tasks,"今日待配送计划",db.count("SELECT COUNT(*) FROM meal_order o JOIN delivery_record d ON d.order_id=o.id WHERE o.site_id IN(SELECT site_id FROM sys_user_site WHERE user_id=?) AND (? IS NULL OR o.site_id=?) AND o.meal_date=? AND o.status IN('PAID','PREPARING','READY') AND d.status='PENDING'",p.userId(),siteId,siteId,today),prefix+"/delivery?date="+today+"&deliveryStatus=PENDING");
  }else if(role.equals("COMMUNITY")){
   task(p,tasks,"待审核绑定",db.count("SELECT COUNT(*) FROM family_binding b JOIN elder e ON e.id=b.elder_id WHERE b.status='PENDING' AND e.site_id IN(SELECT site_id FROM sys_user_site WHERE user_id=?) AND (? IS NULL OR e.site_id=?)",p.userId(),siteId,siteId),prefix+"/elders?tab=bindings"+selection);
   task(p,tasks,"待审核补贴",db.count("SELECT COUNT(*) FROM subsidy_application a JOIN elder e ON e.id=a.elder_id WHERE a.status='PENDING' AND e.site_id IN(SELECT site_id FROM sys_user_site WHERE user_id=?) AND (? IS NULL OR e.site_id=?)",p.userId(),siteId,siteId),prefix+"/subsidies?status=PENDING"+selection);
   task(p,tasks,"待审核退款",db.count("SELECT COUNT(*) FROM refund_record r JOIN meal_order o ON o.id=r.order_id WHERE r.status='PENDING_REVIEW' AND o.site_id IN(SELECT site_id FROM sys_user_site WHERE user_id=?) AND (? IS NULL OR o.site_id=?)",p.userId(),siteId,siteId),prefix+"/refunds?status=PENDING_REVIEW"+selection);
   task(p,tasks,"待受理工单",db.count("SELECT COUNT(*) FROM feedback_ticket WHERE site_id IN(SELECT site_id FROM sys_user_site WHERE user_id=?) AND (? IS NULL OR site_id=?) AND status='OPEN'",p.userId(),siteId,siteId),prefix+"/tickets?status=OPEN"+selection);
  }else if(role.equals("ADMIN")){
   task(p,tasks,"待处理的重大投诉",db.count("SELECT COUNT(*) FROM feedback_ticket WHERE priority='MAJOR' AND status='ESCALATED'"),prefix+"/tickets?status=ESCALATED");
  }
  task(p,tasks,"未读消息",db.count("SELECT COUNT(*) FROM inbox_message WHERE user_id=? AND read_at IS NULL",p.userId()),"/messages?unread=true");
  return ApiResponse.ok(Values.map("tasks",tasks));
 }
 private void task(AuthPrincipal p,List<Map<String,Object>> tasks,String label,long count,String path){String base=path.contains("?")?path.substring(0,path.indexOf('?')):path;if(db.count("SELECT COUNT(*) FROM sys_user_permission WHERE user_id=? AND permission_code=?",p.userId(),"menu:"+base)==0)tasks.add(Values.map("label",label,"count",count,"path",path));}
}
