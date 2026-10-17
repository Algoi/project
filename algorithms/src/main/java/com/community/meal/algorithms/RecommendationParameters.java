package com.community.meal.algorithms;

import java.math.*;
import java.util.*;

public record RecommendationParameters(
    BigDecimal nutritionWeight,
    BigDecimal preferenceWeight,
    BigDecimal reviewWeight,
    BigDecimal historyWeight,
    Map<String, NutrientTarget> targets) {
  public RecommendationParameters {
    var weights = List.of(nutritionWeight, preferenceWeight, reviewWeight, historyWeight);
    if (weights.stream().anyMatch(w -> w.signum() < 0)
        || weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal.ONE) != 0)
      throw new IllegalArgumentException("权重须非负且总和为 1");
    targets = Map.copyOf(targets);
  }
}
