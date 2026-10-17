package com.community.meal.algorithms;

import java.math.*;
import java.util.*;

public record RecommendationCandidate(
    long id,
    String name,
    Map<String, BigDecimal> nutrition,
    double preference,
    double review,
    double history) {
  public RecommendationCandidate {
    Objects.requireNonNull(name);
    nutrition = Map.copyOf(nutrition);
    for (double value : new double[] {preference, review, history})
      if (!Double.isFinite(value) || value < 0 || value > 100)
        throw new IllegalArgumentException("评分信号必须在 0 到 100 之间");
  }
}
