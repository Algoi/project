package com.community.meal.algorithms;
import static org.junit.jupiter.api.Assertions.*;import java.time.*;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;import org.junit.jupiter.api.Test;
class ForecastBacktestTest {
 @Test void rollingEvaluationHidesTargetAndFutureObservationsAndComputesKnownErrors(){
  LocalDate start=LocalDate.of(2026,1,1);List<AttendanceSample> samples=new ArrayList<>();for(int i=0;i<5;i++)samples.add(new AttendanceSample(start.plusDays(i),10*(i+1)));samples.add(new AttendanceSample(start.plusDays(50),999999));AtomicInteger calls=new AtomicInteger();
  ForecastAlgorithm checked=new ForecastAlgorithm(){public String id(){return "checked";}public List<ForecastPoint> forecast(ForecastRequest request){LocalDate target=request.dates().get(0);assertTrue(request.history().stream().allMatch(s->s.date().isBefore(target)&&!s.date().isBefore(target.minusDays(56))));calls.incrementAndGet();return new WeekdayAverageForecast().forecast(request);}};
  var report=ForecastBacktest.evaluate(checked,samples,start,start.plusDays(5),Set.of());
  assertEquals(2,report.evaluated());assertEquals(3,report.insufficient());assertEquals(1,report.missing());assertEquals(22.5,report.mae());assertEquals(Math.sqrt(512.5),report.rmse(),0.00001);assertEquals(5,calls.get());
 }
 @Test void emptyOrClosedTargetsDoNotProduceInventedAccuracy(){var day=LocalDate.of(2026,1,1);var report=ForecastBacktest.evaluate(new RecentWeightedForecast(),List.of(new AttendanceSample(day,0)),day,day.plusDays(1),Set.of(day));assertEquals(0,report.evaluated());assertNull(report.mae());assertEquals(1,report.closed());assertEquals(1,report.missing());}
}
