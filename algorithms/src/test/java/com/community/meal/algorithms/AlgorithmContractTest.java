package com.community.meal.algorithms;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class AlgorithmContractTest {
  private final LocalDate target = LocalDate.of(2026, 10, 18);

  @Test
  void weekdayBaselineMatchesExistingBehaviorAndNeverMutatesHistory() {
    var history =
        List.of(
            new AttendanceSample(target.minusWeeks(1), 10),
            new AttendanceSample(target.minusWeeks(2), 20),
            new AttendanceSample(target.minusWeeks(3), 30));
    var result =
        new WeekdayAverageForecast()
            .forecast(new ForecastRequest(history, List.of(target), Set.of()));
    assertEquals(20, result.get(0).count());
    assertEquals(3, history.size());
    assertEquals(3, result.get(0).sampleDates().size());
  }

  @Test
  void missingHistoryAndClosureAreDifferentFromObservedZero() {
    var algorithm = new WeekdayAverageForecast();
    assertNull(
        algorithm
            .forecast(new ForecastRequest(List.of(), List.of(target), Set.of()))
            .get(0)
            .count());
    assertTrue(
        algorithm
            .forecast(new ForecastRequest(List.of(), List.of(target), Set.of(target)))
            .get(0)
            .closed());
    var zeros =
        List.of(
            new AttendanceSample(target.minusDays(1), 0),
            new AttendanceSample(target.minusDays(2), 0),
            new AttendanceSample(target.minusDays(3), 0));
    assertEquals(
        0,
        algorithm.forecast(new ForecastRequest(zeros, List.of(target), Set.of())).get(0).count());
  }

  @Test
  void implementationsCanBeSelectedWithoutChangingCaller() {
    var history =
        List.of(
            new AttendanceSample(target.minusDays(1), 30),
            new AttendanceSample(target.minusDays(2), 20),
            new AttendanceSample(target.minusDays(3), 10));
    var request = new ForecastRequest(history, List.of(target), Set.of());
    var registry =
        new AlgorithmRegistry<ForecastAlgorithm>(
            List.of(new WeekdayAverageForecast(), new RecentWeightedForecast()));
    assertEquals(20, registry.select("weekday_average_v1").forecast(request).get(0).count());
    assertEquals(
        23, registry.select("recent_weighted_average_v1").forecast(request).get(0).count());
    assertThrows(IllegalArgumentException.class, () -> registry.select("missing"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AlgorithmRegistry<ForecastAlgorithm>(
                List.of(new WeekdayAverageForecast(), new WeekdayAverageForecast())));
  }

  @Test
  void weightedScoreAndStableTiesMatchOriginalExample() {
    var parameters =
        new RecommendationParameters(
            new BigDecimal("0.4"),
            new BigDecimal("0.25"),
            new BigDecimal("0.2"),
            new BigDecimal("0.15"),
            Map.of("energy", new NutrientTarget(new BigDecimal("100"), new BigDecimal("100"))));
    var candidate =
        new RecommendationCandidate(2, "菜品", Map.of("energy", new BigDecimal("80")), 100, 60, 50);
    var result = new WeightedRecommendation().rank(List.of(candidate), parameters);
    assertEquals(new BigDecimal("76.50"), result.get(0).score());
    var ties =
        new WeightedRecommendation()
            .rank(
                List.of(
                    new RecommendationCandidate(3, "同名", Map.of(), 50, 50, 50),
                    new RecommendationCandidate(1, "同名", Map.of(), 50, 50, 50)),
                parameters);
    assertEquals(List.of(1L, 3L), ties.stream().map(RankedCandidate::id).toList());
  }

  @Test
  void invalidInputsAreRejectedAtTheContract() {
    assertThrows(IllegalArgumentException.class, () -> new AttendanceSample(target, -1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RecommendationParameters(
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, Map.of()));
    assertThrows(
        IllegalArgumentException.class, () -> new NutrientTarget(BigDecimal.TEN, BigDecimal.ONE));
  }
}
