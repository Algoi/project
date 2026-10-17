package com.community.meal.algorithms;

import java.math.*;
import java.time.LocalDate;
import java.util.*;

public record ForecastRequest(
    List<AttendanceSample> history, List<LocalDate> dates, Set<LocalDate> closedDates) {
  public ForecastRequest {
    history =
        history.stream().sorted(Comparator.comparing(AttendanceSample::date).reversed()).toList();
    dates = List.copyOf(dates);
    closedDates = Set.copyOf(closedDates);
    if (dates.isEmpty() || dates.size() > 7) throw new IllegalArgumentException("预测日期须为 1 到 7 天");
  }
}
