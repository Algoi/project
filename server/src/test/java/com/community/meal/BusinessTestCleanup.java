package com.community.meal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
final class BusinessTestCleanup {
 static void clear(JdbcTemplate jdbc){
  if(!jdbc.queryForObject("SELECT DATABASE()",String.class).startsWith("elder_meal_test_"))throw new IllegalStateException("拒绝清理非临时测试数据库");
  for(String table:List.of("binding_history","subsidy_history","menu_template","order_event","inbox_message","delivery_address","wallet_ledger","wallet_topup","wallet_account","dish_review","redemption","delivery_record","subsidy_ledger","refund_record","payment_record","meal_order_item","meal_order","recommendation_event","preparation_item","daily_menu_item","daily_menu","subsidy_entitlement","subsidy_application","family_binding","elder_health","elder","dish_allergen","dish","site_closed_day","attendance_daily","forecast_result","sys_user_permission","sys_user_site","auth_session","sys_user_role","service_site","sys_user","audit_log"))jdbc.update("DELETE FROM "+table);
 }
}
