package com.community.meal;
import static org.assertj.core.api.Assertions.*;import java.util.*;import java.time.*;import org.junit.jupiter.api.Test;import org.springframework.transaction.annotation.*;
/** Runs across real service transaction boundaries, so rollback is observed after the failed request. */
class MenuTemplateAtomicityTest extends WalletTestSupport {
 @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
 void conflictOnTheSecondMealRollsBackTheWholeWeek()throws Exception{
  try{
   long order=order(),menu=jdbc.queryForObject("SELECT menu_id FROM daily_menu_item WHERE id=(SELECT menu_item_id FROM meal_order_item WHERE order_id=? LIMIT 1)",Long.class,order);
   LocalDate source=LocalDate.now().plusWeeks(2).with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)),target=source.plusWeeks(1);
   call("POST","/sites/1/menus/"+menu+"/copy",Map.of("serviceDate",source.toString(),"mealType","LUNCH"),"canteen",200);
   call("POST","/sites/1/menus/"+menu+"/copy",Map.of("serviceDate",source.plusDays(1).toString(),"mealType","DINNER"),"canteen",200);
   long template=call("POST","/sites/1/menu-templates",Map.of("name","完整事务回滚验证","weekStart",source.toString()),"canteen",200).path("id").asLong();
   call("POST","/sites/1/menus/"+menu+"/copy",Map.of("serviceDate",target.plusDays(1).toString(),"mealType","DINNER"),"canteen",200);
   call("POST","/sites/1/menu-templates/"+template+"/apply",Map.of("weekStart",target.toString()),"canteen",409);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_menu WHERE service_date=?",Long.class,target)).isZero();
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_menu WHERE service_date=?",Long.class,target.plusDays(1))).isEqualTo(1);
  }finally{BusinessTestCleanup.clear(jdbc);}
 }
}
