package com.community.meal.forecast;

import static com.community.meal.common.Values.*;

import com.community.meal.algorithms.*;
import com.community.meal.auth.Access;
import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForecastService implements Forecasts {
  private final Db db;
  private final Access access;
  private final BookingSites sites;
  private final OrderDemand demand;
  private final AlgorithmSelection algorithms;

  public ForecastService(
      Db db, Access access, BookingSites sites, OrderDemand demand, AlgorithmSelection algorithms) {
    this.db = db;
    this.access = access;
    this.sites = sites;
    this.demand = demand;
    this.algorithms = algorithms;
  }

  public List<Map<String, Object>> history(Actor actor, long site, String meal) {
    access.role(actor, "ADMIN", "CANTEEN");
    access.site(actor, site);
    return history(site, meal);
  }

  private List<Map<String, Object>> history(long site, String meal) {
    LocalDate now = LocalDate.now(BusinessTime.ZONE);
    var closed = sites.closedDates(site, now.minusDays(56), now.minusDays(1), meal);
    return demand.attendance(site, meal, now.minusDays(56), now).stream()
        .filter(r -> !closed.contains(date(r.get("date"))))
        .toList();
  }

  @Transactional
  public Map<String, Object> predict(
      Actor actor, long site, String meal, LocalDate start, int days) {
    access.role(actor, "ADMIN", "CANTEEN");
    access.site(actor, site);
    return calculate(site, meal, start, days);
  }

  public Map<String, Object> calculate(long site, String meal, LocalDate start, int days) {
    if (days < 1 || days > 7) throw ApiException.invalid("预测天数应为 1 到 7 天");
    var history = history(site, meal);
    var dates = java.util.stream.IntStream.range(0, days).mapToObj(start::plusDays).toList();
    var closed = new HashSet<>(sites.closedDates(site, start, start.plusDays(days - 1), meal));
    if (!str(sites.site(site).get("businessStatus")).equals("OPEN")) closed.addAll(dates);
    var request =
        new ForecastRequest(
            history.stream()
                .map(
                    r ->
                        new AttendanceSample(
                            date(r.get("date")), Math.toIntExact(num(r.get("count")))))
                .toList(),
            dates,
            closed);
    var algorithm = algorithms.forecast();
    List<ForecastPoint> calculated;
    try { calculated = algorithm.forecast(request); }
    catch(RuntimeException failure) { throw new AlgorithmUnavailableException(); }
    if (calculated.size() != dates.size()) throw new IllegalStateException("预测算法未返回全部日期");
    List<Map<String, Object>> points = new ArrayList<>();
    for (int i = 0; i < calculated.size(); i++) {
      var point = calculated.get(i);
      if (!point.date().equals(dates.get(i))) throw new IllegalStateException("预测算法返回日期不匹配");
      db.insert(
          "INSERT INTO"
              + " forecast_result(site_id,meal_type,target_date,predicted_count,closed,insufficient_data,sample_count,algorithm,sample_dates_json)VALUES(?,?,?,?,?,?,?,?,?)",
          site,
          meal,
          point.date(),
          point.count(),
          point.closed(),
          point.insufficientData(),
          point.sampleDates().size(),
          algorithm.id(),
          db.json(point.sampleDates()));
      points.add(
          map(
              "date",
              point.date().toString(),
              "count",
              point.count(),
              "sampleCount",
              point.sampleDates().size(),
              "sampleDates",
              point.sampleDates(),
              "closed",
              point.closed(),
              "insufficientData",
              point.insufficientData()));
    }
    return map(
        "algorithm",
        algorithm.id(),
        "points",
        points,
        "history",
        history,
        "generatedAt",
        Instant.now().toString());
  }
}
