package com.community.meal.algorithms;

import java.math.*;
import java.util.*;

public final class WeightedRecommendation implements RecommendationAlgorithm {
  public String id() {
    return "weighted_score_v1";
  }

  public List<RankedCandidate> rank(
      List<RecommendationCandidate> candidates, RecommendationParameters parameters) {
    Map<Long, String> names = new HashMap<>();
    List<RankedCandidate> ranked = new ArrayList<>();
    for (var candidate : candidates) {
      names.put(candidate.id(), candidate.name());
      double nutrition = nutrition(candidate.nutrition(), parameters.targets());
      var components =
          new ScoreComponents(
              nutrition, candidate.preference(), candidate.review(), candidate.history());
      ranked.add(new RankedCandidate(candidate.id(), score(components, parameters), components));
    }
    ranked.sort(
        Comparator.comparing(RankedCandidate::score)
            .reversed()
            .thenComparing(r -> names.get(r.id()))
            .thenComparingLong(RankedCandidate::id));
    return List.copyOf(ranked);
  }

  public static BigDecimal score(ScoreComponents c, RecommendationParameters p) {
    return p.nutritionWeight()
        .multiply(BigDecimal.valueOf(c.nutrition()))
        .add(p.preferenceWeight().multiply(BigDecimal.valueOf(c.preference())))
        .add(p.reviewWeight().multiply(BigDecimal.valueOf(c.review())))
        .add(p.historyWeight().multiply(BigDecimal.valueOf(c.history())))
        .setScale(2, RoundingMode.HALF_UP);
  }

  private double nutrition(Map<String, BigDecimal> values, Map<String, NutrientTarget> targets) {
    if (targets.isEmpty()) return 50;
    double total = 0;
    for (var entry : targets.entrySet()) {
      var actual = values.get(entry.getKey());
      if (actual == null) {
        total += 50;
        continue;
      }
      double n = actual.doubleValue(),
          min = entry.getValue().min().doubleValue(),
          max = entry.getValue().max().doubleValue();
      total +=
          n >= min && n <= max
              ? 100
              : Math.max(
                  0,
                  100
                      - 100
                          * Math.abs(n - (n < min ? min : max))
                          / Math.max(1, n < min ? min : max));
    }
    return total / targets.size();
  }
}
