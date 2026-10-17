package com.community.meal;
import static org.assertj.core.api.Assertions.*;import java.time.*;import org.junit.jupiter.api.Test;
class BacktestBusinessTest extends BusinessTestSupport {
 @Test void historicalEvaluationIsScopedAndDoesNotPersistOperationalForecasts()throws Exception{
  LocalDate to=LocalDate.now(com.community.meal.common.BusinessTime.ZONE).minusDays(1),from=to.minusDays(4);
  for(int i=0;i<5;i++)jdbc.update("INSERT INTO attendance_daily(site_id,attendance_date,meal_type,diner_count,is_open)VALUES(1,?,'LUNCH',?,1)",from.plusDays(i),10*(i+1));
  String query="?dateFrom="+from+"&dateTo="+to;long before=jdbc.queryForObject("SELECT COUNT(*) FROM forecast_result",Long.class);
  var result=call("GET","/sites/1/forecast-backtest"+query,null,"canteen",200);assertThat(result.path("reports").size()).isEqualTo(2);assertThat(result.path("reports").get(0).path("evaluated").asInt()).isEqualTo(2);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM forecast_result",Long.class)).isEqualTo(before);
  call("GET","/sites/2/forecast-backtest"+query,null,"canteen",403);call("GET","/sites/1/forecast-backtest"+query,null,"family",403);
  call("GET","/sites/1/forecast-backtest?dateFrom="+to+"&dateTo="+to.plusDays(1),null,"canteen",422);
 }
}
