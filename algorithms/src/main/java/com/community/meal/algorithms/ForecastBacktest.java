package com.community.meal.algorithms;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Rolling-origin one-step evaluation. No target or future value enters the forecast request. */
public final class ForecastBacktest {
  private ForecastBacktest() {}
  public record Observation(LocalDate date,Integer actual,Integer predicted,List<LocalDate> sampleDates,String status) {}
  public record Report(String algorithm,int evaluated,int insufficient,int missing,int closed,Double mae,Double rmse,List<Observation> observations) {}
  public static Report evaluate(ForecastAlgorithm algorithm,List<AttendanceSample> samples,LocalDate from,LocalDate to,Set<LocalDate> closedDates){
    if(to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>89)throw new IllegalArgumentException("回测区间应为 1 至 90 天");
    Map<LocalDate,Integer> actuals=new HashMap<>();for(var sample:samples)if(actuals.putIfAbsent(sample.date(),sample.count())!=null)throw new IllegalArgumentException("重复的实际人数日期");
    List<Observation> rows=new ArrayList<>();int evaluated=0,insufficient=0,missing=0,closed=0;double absolute=0,squared=0;
    for(LocalDate day=from;!day.isAfter(to);day=day.plusDays(1)){
      Integer actual=actuals.get(day);
      if(closedDates.contains(day)){closed++;rows.add(new Observation(day,actual,null,List.of(),"CLOSED"));continue;}
      if(actual==null){missing++;rows.add(new Observation(day,null,null,List.of(),"MISSING_ACTUAL"));continue;}
      LocalDate target=day;var training=samples.stream().filter(s->s.date().isBefore(target)&&!s.date().isBefore(target.minusDays(56))&&!closedDates.contains(s.date())).toList();
      var points=algorithm.forecast(new ForecastRequest(training,List.of(target),Set.of()));
      if(points.size()!=1||!points.get(0).date().equals(target))throw new IllegalStateException("回测算法返回日期不匹配");
      var point=points.get(0);Set<LocalDate> allowed=new HashSet<>();training.forEach(s->allowed.add(s.date()));
      if(point.closed()||!allowed.containsAll(point.sampleDates())||point.count()!=null&&point.count()<0)throw new IllegalStateException("回测算法结果不符合输入契约");
      if(point.count()==null){insufficient++;rows.add(new Observation(day,actual,null,point.sampleDates(),"INSUFFICIENT"));continue;}
      double error=(double)point.count()-actual;absolute+=Math.abs(error);squared+=error*error;evaluated++;
      rows.add(new Observation(day,actual,point.count(),point.sampleDates(),"EVALUATED"));
    }
    return new Report(algorithm.id(),evaluated,insufficient,missing,closed,evaluated==0?null:absolute/evaluated,evaluated==0?null:Math.sqrt(squared/evaluated),List.copyOf(rows));
  }
}
