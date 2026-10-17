package com.community.meal;
import static org.assertj.core.api.Assertions.*;import java.util.*;import java.time.*;import org.junit.jupiter.api.Test;
class LifecycleBusinessTest extends WalletTestSupport {
 @Test void bindingRevocationRemovesPrivateAccessAndKeepsAuditedHistory() throws Exception {
  long order=order(),elder=jdbc.queryForObject("SELECT elder_id FROM meal_order WHERE id=?",Long.class,order),binding=jdbc.queryForObject("SELECT id FROM family_binding WHERE elder_id=?",Long.class,elder);
  call("POST","/bindings/"+binding+"/revoke",Map.of("note","关系变更"),"family",200);
  call("GET","/orders/"+order,null,"family",403);call("GET","/elders/"+elder+"/health",null,"family",403);
  assertThat(call("GET","/family/bindings",null,"family",200).get(0).path("status").asText()).isEqualTo("REVOKED");
  assertThat(call("GET","/bindings/history",null,"community",200).path("records").findValuesAsText("action")).contains("REVOKED");
  call("POST","/family/bindings",Map.of("elderId",elder),"family",200);
  assertThat(call("GET","/bindings/history",null,"community",200).path("total").asInt()).isEqualTo(2);
 }
 @Test void subsidyValidityUsesTheMealDateAndTerminationPreservesHistoricalPrices() throws Exception {
  long order=order(),elder=jdbc.queryForObject("SELECT elder_id FROM meal_order WHERE id=?",Long.class,order);String old=jdbc.queryForObject("SELECT payable_amount FROM meal_order WHERE id=?",String.class,order);
  long app=call("POST","/subsidy-applications",Map.of("elderId",elder),"family",200).path("id").asLong();String today=LocalDate.now(com.community.meal.common.BusinessTime.ZONE).toString();
  call("POST","/subsidy-applications/"+app+"/approve",Map.of("note","仅当日生效","validFrom",today,"validTo",today),"community",200);
  var menu=jdbc.queryForMap("SELECT site_id,meal_date,meal_type FROM meal_order WHERE id=?",order);
  var rec=call("GET","/elders/"+elder+"/recommendations?siteId=1&date="+menu.get("meal_date")+"&mealType=LUNCH",null,"family",200);
  assertThat(rec.path("items").get(0).path("effectivePrice").asText()).isEqualTo("12.50");
  call("POST","/elders/"+elder+"/subsidy/terminate",Map.of("note","资格终止"),"family",403);
  call("POST","/elders/"+elder+"/subsidy/terminate",Map.of("note","资格终止"),"community",200);
  assertThat(jdbc.queryForObject("SELECT payable_amount FROM meal_order WHERE id=?",String.class,order)).isEqualTo(old);
  assertThat(call("GET","/elders/"+elder+"/subsidy-history",null,"family",200).path("total").asInt()).isEqualTo(2);
 }
 @Test void menuCopyAndWeeklyTemplatesCreateDraftsWithoutOverwritingExistingMenus() throws Exception {
  long order=order(),menu=jdbc.queryForObject("SELECT menu_id FROM daily_menu_item WHERE id=(SELECT menu_item_id FROM meal_order_item WHERE order_id=? LIMIT 1)",Long.class,order);
  String target=LocalDate.now().plusWeeks(2).with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).toString();
  var body=Map.of("serviceDate",target,"mealType","LUNCH");var copied=call("POST","/sites/1/menus/"+menu+"/copy",body,"canteen",200);
  assertThat(copied.path("status").asText()).isEqualTo("DRAFT");assertThat(copied.path("items").size()).isEqualTo(1);
  call("POST","/sites/1/menus/"+menu+"/copy",body,"canteen",409);call("POST","/sites/2/menus/"+menu+"/copy",body,"canteen",403);
  long template=call("POST","/sites/1/menu-templates",Map.of("name","完整周模板","weekStart",target),"canteen",200).path("id").asLong();
  String next=LocalDate.parse(target).plusWeeks(1).toString();var applied=call("POST","/sites/1/menu-templates/"+template+"/apply",Map.of("weekStart",next),"canteen",200);
  assertThat(applied.path("menus").get(0).path("status").asText()).isEqualTo("DRAFT");
  call("POST","/sites/1/menu-templates/"+template+"/apply",Map.of("weekStart",next),"canteen",409);

 }
}
