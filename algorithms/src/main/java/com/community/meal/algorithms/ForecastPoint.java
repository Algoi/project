package com.community.meal.algorithms;

import java.math.*;
import java.time.LocalDate;
import java.util.*;

public record ForecastPoint(
    LocalDate date, Integer count, List<LocalDate> sampleDates, boolean closed) {
  public ForecastPoint {
    sampleDates = List.copyOf(sampleDates);
  }

  public boolean insufficientData() {
    return !closed && count == null;
  }
}
