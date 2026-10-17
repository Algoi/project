package com.community.meal.forecast;
import com.community.meal.auth.Access;import com.community.meal.auth.AuthPrincipal;import com.community.meal.common.*;import com.community.meal.contracts.*;import com.community.meal.algorithms.*;import java.util.*;import java.time.*;
import org.springframework.web.bind.annotation.*;import org.springframework.security.core.annotation.AuthenticationPrincipal;
@RestController @RequestMapping("/api/v1/sites/{siteId}/forecast-backtest")
public class BacktestController {
 private final Access access;private final BookingSites sites;private final OrderDemand demand;private final List<ForecastAlgorithm> algorithms;
 public BacktestController(Access access,BookingSites sites,OrderDemand demand,List<ForecastAlgorithm> algorithms){this.access=access;this.sites=sites;this.demand=demand;this.algorithms=algorithms;}
 @GetMapping public ApiResponse<?> evaluate(@AuthenticationPrincipal AuthPrincipal p,@PathVariable long siteId,@RequestParam String dateFrom,@RequestParam String dateTo,@RequestParam(defaultValue="LUNCH")String mealType){
  access.role(p,"ADMIN","CANTEEN");access.site(p,siteId);LocalDate from=Values.date(dateFrom),to=Values.date(dateTo),today=LocalDate.now(BusinessTime.ZONE);String meal=Values.meal(mealType);
  Values.rule(!to.isBefore(from)&&java.time.temporal.ChronoUnit.DAYS.between(from,to)<=89&&to.isBefore(today),"回测须选择今天之前的 1 至 90 天");
  var samples=demand.attendance(siteId,meal,from.minusDays(56),to.plusDays(1)).stream().map(r->new AttendanceSample(Values.date(r.get("date")),Math.toIntExact(Values.num(r.get("count"))))).toList();
  var closed=sites.closedDates(siteId,from.minusDays(56),to,meal);
  var reports=algorithms.stream().sorted(Comparator.comparing(ForecastAlgorithm::id)).map(a->ForecastBacktest.evaluate(a,samples,from,to,closed)).toList();
  return ApiResponse.ok(Values.map("dateFrom",from,"dateTo",to,"lookbackDays",56,"reports",reports,"notice","滚动回测仅使用目标日之前的历史实际人数；缺失和停餐不计误差。演示数据不能代表真实业务准确率。"));
 }
}
