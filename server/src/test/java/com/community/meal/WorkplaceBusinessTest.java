package com.community.meal;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WorkplaceBusinessTest extends WalletTestSupport {
 @Test void workbenchCountsOnlyAuthorizedOrdersAndLinksToTheirFilter() throws Exception {
  order();var board=call("GET","/workbench?role=FAMILY",null,"family",200);
  assertThat(board.path("tasks").findValuesAsText("label")).contains("待付款");
  assertThat(board.path("tasks").get(0).path("count").asInt()).isEqualTo(1);
  assertThat(board.path("tasks").get(0).path("path").asText()).contains("status=PENDING_PAYMENT");
  call("GET","/workbench?role=ADMIN",null,"family",403);
  call("GET","/workbench?role=CANTEEN&siteId=2",null,"canteen",403);
 }
 @Test void approvalMessagesBelongToTheApplicantAndReadOperationsDoNotCrossAccounts() throws Exception {
  long elder=call("POST","/elders",Map.of("name","消息审核老人","siteId",1),"community",200).path("id").asLong();
  long binding=call("POST","/family/bindings",Map.of("elderId",elder),"family",200).path("id").asLong();
  call("POST","/bindings/"+binding+"/approve",Map.of("note","审核通过"),"community",200);
  var inbox=call("GET","/messages",null,"family",200);assertThat(inbox.path("total").asInt()).isGreaterThan(0);
  long message=inbox.path("records").get(0).path("id").asLong();
  call("PATCH","/messages/"+message+"/read",Map.of(),"community",403);
  call("PATCH","/messages/"+message+"/read",Map.of(),"family",200);
  call("PATCH","/messages/"+message+"/read",Map.of(),"family",200);
  assertThat(call("GET","/messages/unread-count",null,"family",200).path("count").asInt()).isZero();
 }
 @Test void savedAddressesAreScopedToOwnerAndCurrentBindingAndDoNotRewriteOrders() throws Exception {
  long order=order();long elder=jdbc.queryForObject("SELECT elder_id FROM meal_order WHERE id=?",Long.class,order);
  var body=new HashMap<String,Object>(Map.of("elderId",elder,"label","家里","contactName","测试家属","contactPhone","13800000000","address","原常用地址","isDefault",true));
  long address=call("POST","/addresses",body,"family",200).path("id").asLong();
  body.put("address","第二常用地址");long second=call("POST","/addresses",body,"family",200).path("id").asLong();
  assertThat(call("GET","/addresses?elderId="+elder,null,"family",200).path("records").findValues("isDefault").stream().filter(n->n.asBoolean()).count()).isEqualTo(1);
  jdbc.update("UPDATE meal_order SET delivery_address='历史配送地址' WHERE id=?",order);
  body.put("address","修改常用地址");call("PUT","/addresses/"+address,body,"family",200);
  assertThat(jdbc.queryForObject("SELECT delivery_address FROM meal_order WHERE id=?",String.class,order)).isEqualTo("历史配送地址");
  call("DELETE","/addresses/"+second,null,"community",403);
  jdbc.update("UPDATE family_binding SET status='REVOKED' WHERE elder_id=?",elder);
  assertThat(call("GET","/addresses",null,"family",200).path("total").asInt()).isZero();
  call("PUT","/addresses/"+address,body,"family",403);
 }
 @Test void orderEventsAndMessagesDoNotDuplicateOnPaymentRetriesAndRollBackTogether() throws Exception {
  long id=order();assertThat(call("GET","/orders/"+id,null,"family",200).path("events").size()).isEqualTo(1);
  String request="events"+System.nanoTime();long topup=call("POST","/wallet/topups",Map.of("amount","100.00","requestId",request),"family",200).path("id").asLong();
  call("POST","/dev/wallet/topups/"+topup+"/confirm",Map.of(),"family",200);
  call("POST","/orders/"+id+"/payments",Map.of("channel","WALLET"),"family",200);
  long messages=jdbc.queryForObject("SELECT COUNT(*) FROM inbox_message WHERE user_id=13",Long.class);
  call("POST","/orders/"+id+"/payments",Map.of("channel","WALLET"),"family",200);
  assertThat(call("GET","/orders/"+id,null,"family",200).path("events").size()).isEqualTo(2);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inbox_message WHERE user_id=13",Long.class)).isEqualTo(messages);
 }

 @Test void targetedNoticesAndClosureAlertsRespectAudienceAndRepeatedPublishDoesNotDuplicate() throws Exception {
  long id=order();String date=jdbc.queryForObject("SELECT meal_date FROM meal_order WHERE id=?",String.class,id);
  long notice=call("POST","/notices",Map.of("title","定向公告","type","GENERAL","content","测试内容","receiveRoles",List.of("FAMILY"),"siteIds",List.of(1)),"admin",200).path("id").asLong();
  call("POST","/notices/"+notice+"/publish",Map.of(),"admin",200);
  long count=jdbc.queryForObject("SELECT COUNT(*) FROM inbox_message WHERE user_id=13",Long.class);
  assertThat(count).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inbox_message WHERE user_id=14",Long.class)).isZero();
  call("POST","/notices/"+notice+"/publish",Map.of(),"admin",200);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inbox_message WHERE user_id=13",Long.class)).isEqualTo(count);
  call("PUT","/sites/1/closed-dates",Map.of("dates",List.of(Map.of("date",date,"mealType","LUNCH","reason","测试停餐"))),"admin",200);
  assertThat(call("GET","/messages",null,"family",200).path("records").findValuesAsText("title")).contains("供餐安排调整涉及您的订单");
 }

 @Test void workbenchLinksAndListsUseTheSameSelectedSiteForMultiSiteOperators()throws Exception{
  jdbc.update("INSERT INTO sys_user_site(user_id,site_id)VALUES(14,2)");
  long elder=call("POST","/elders",Map.of("name","第二网点待办老人","siteId",2),"community",200).path("id").asLong();
  long binding=call("POST","/family/bindings",Map.of("elderId",elder),"family",200).path("id").asLong();
  var first=call("GET","/workbench?role=COMMUNITY&siteId=1",null,"community",200).path("tasks").get(0);
  assertThat(first.path("count").asInt()).isZero();assertThat(first.path("path").asText()).contains("siteId=1");
  assertThat(call("GET","/bindings?siteId=1",null,"community",200).path("total").asInt()).isZero();
  assertThat(call("GET","/bindings?siteId=2",null,"community",200).path("total").asInt()).isEqualTo(1);
  call("POST","/bindings/"+binding+"/approve",Map.of("note","多网点筛选核对"),"community",200);
  assertThat(call("GET","/bindings?siteId=2&status=",null,"community",200).path("total").asInt()).isEqualTo(1);
  assertThat(call("GET","/bindings?siteId=2&status=PENDING",null,"community",200).path("total").asInt()).isZero();
  call("POST","/subsidy-applications",Map.of("elderId",elder),"family",200);
  call("POST","/tickets",Map.of("siteId",2,"type","FEEDBACK","content","第二网点工单"),"family",200);
  for(String endpoint:List.of("/subsidy-applications","/tickets")){
   assertThat(call("GET",endpoint+"?siteId=1",null,"community",200).path("total").asInt()).isZero();
   assertThat(call("GET",endpoint+"?siteId=2",null,"community",200).path("total").asInt()).isEqualTo(1);
  }
  long id=order(),payment=call("POST","/orders/"+id+"/payments",Map.of("channel","MOCK_A"),"family",200).path("id").asLong();
  call("POST","/dev/payments/"+payment+"/simulate-success",Map.of(),"family",200);
  call("POST","/orders/"+id+"/cancel",Map.of("reason","验证本网点退款筛选"),"family",200);
  assertThat(call("GET","/refunds?siteId=1",null,"community",200).path("total").asInt()).isEqualTo(1);
  assertThat(call("GET","/refunds?siteId=2",null,"community",200).path("total").asInt()).isZero();

 }

}
