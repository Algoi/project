package com.community.meal.algorithms;

import java.math.*;
import java.time.LocalDate;
import java.util.*;

/** Alternative implementation; newer actual observations have linearly greater weight. */
public final class RecentWeightedForecast implements ForecastAlgorithm {
  public String id() {
    return "recent_weighted_average_v1";
  }

  public List<ForecastPoint> forecast(ForecastRequest request) {
    List<ForecastPoint> results = new ArrayList<>();
    for (LocalDate day : request.dates()) {
      if (request.closedDates().contains(day)) {
        results.add(new ForecastPoint(day, 0, List.of(), true));
        continue;
      }
      var samples =
          request.history().stream().filter(s -> s.date().isBefore(day)).limit(14).toList();
      Integer count = null;
      if (samples.size() >= 3) {
        long sum = 0, weights = 0;
        for (int i = 0; i < samples.size(); i++) {
          long weight = samples.size() - i;
          sum += samples.get(i).count() * weight;
          weights += weight;
        }
        count =
            BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(weights), 0, RoundingMode.HALF_UP)
                .intValue();
      }
      results.add(
          new ForecastPoint(
              day, count, samples.stream().map(AttendanceSample::date).toList(), false));
    }
    return List.copyOf(results);
  }
}
