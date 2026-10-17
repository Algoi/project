package com.community.meal.algorithms;

import java.math.*;
import java.time.LocalDate;
import java.util.*;

/** Existing baseline: matching weekdays first, then up to fourteen recent observations. */
public final class WeekdayAverageForecast implements ForecastAlgorithm {
  public String id() {
    return "weekday_average_v1";
  }

  public List<ForecastPoint> forecast(ForecastRequest request) {
    List<ForecastPoint> results = new ArrayList<>();
    for (LocalDate day : request.dates()) {
      if (request.closedDates().contains(day)) {
        results.add(new ForecastPoint(day, 0, List.of(), true));
        continue;
      }
      var history = request.history().stream().filter(s -> s.date().isBefore(day)).toList();
      var samples =
          history.stream()
              .filter(s -> s.date().getDayOfWeek() == day.getDayOfWeek())
              .limit(8)
              .toList();
      if (samples.size() < 3) samples = history.stream().limit(14).toList();
      Integer count = null;
      if (samples.size() >= 3)
        count =
            BigDecimal.valueOf(samples.stream().mapToLong(AttendanceSample::count).sum())
                .divide(BigDecimal.valueOf(samples.size()), 0, RoundingMode.HALF_UP)
                .intValue();
      results.add(
          new ForecastPoint(
              day, count, samples.stream().map(AttendanceSample::date).toList(), false));
    }
    return List.copyOf(results);
  }
}
