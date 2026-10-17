package com.community.meal;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AlgorithmEdgeTest extends BusinessTestSupport {
  @Test
  void forecastFallbackExcludesMissingAndClosedButRetainsRealZero() throws Exception {
    LocalDate target = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1);
    int[] counts = {0, 10, 20};
    for (int i = 0; i < 3; i++)
      jdbc.update(
          "INSERT INTO"
              + " attendance_daily(site_id,attendance_date,meal_type,diner_count)VALUES(1,?,'LUNCH',?)",
          target.minusDays(2 + i),
          counts[i]);
    var first =
        call(
                "GET",
                "/sites/1/forecasts?startDate=" + target + "&days=1&mealType=LUNCH",
                null,
                "canteen",
                200)
            .path("points")
            .get(0);
    assertThat(first.path("count").asInt()).isEqualTo(10);
    assertThat(first.path("sampleCount").asInt()).isEqualTo(3);
    jdbc.update(
        "INSERT INTO site_closed_day(site_id,closed_date,meal_type)VALUES(1,?,'LUNCH')",
        target.minusDays(2));
    var second =
        call(
                "GET",
                "/sites/1/forecasts?startDate=" + target + "&days=1&mealType=LUNCH",
                null,
                "canteen",
                200)
            .path("points")
            .get(0);
    assertThat(second.path("count").isNull()).isTrue();
  }

  @Test
  void weightedRecommendationExampleIsReproducible() {
    Map<String, Object> weights =
        Map.of(
            "nutritionWeight",
            "0.4",
            "preferenceWeight",
            "0.25",
            "reviewWeight",
            "0.2",
            "historyWeight",
            "0.15");
    assertThat(
            com.community.meal.algorithms.WeightedRecommendation.score(
                new com.community.meal.algorithms.ScoreComponents(80, 100, 60, 50),
                new com.community.meal.algorithms.RecommendationParameters(
                    new java.math.BigDecimal("0.4"),
                    new java.math.BigDecimal("0.25"),
                    new java.math.BigDecimal("0.2"),
                    new java.math.BigDecimal("0.15"),
                    Map.of())))
        .isEqualByComparingTo("76.50");
  }

  @Test
  void personalPreferencesArePersistedAndPrivate() throws Exception {
    call(
        "PUT",
        "/auth/preferences",
        Map.of("preferredCategoryIds", List.of(), "largeText", true),
        "family",
        200);
    assertThat(call("GET", "/auth/preferences", null, "family", 200).path("largeText").asBoolean())
        .isTrue();
    assertThat(call("GET", "/auth/preferences", null, "canteen", 200).isNull()).isTrue();
  }
}
