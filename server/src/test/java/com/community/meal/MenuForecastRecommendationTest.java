package com.community.meal;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class MenuForecastRecommendationTest extends BusinessTestSupport {
  @Test
  void emptyHistoryReturnsNullInsteadOfZeroOrError() throws Exception {
    String day =
        java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1).toString();
    var point =
        call(
                "GET",
                "/sites/1/forecasts?startDate=" + day + "&days=1&mealType=LUNCH",
                null,
                "canteen",
                200)
            .path("points")
            .get(0);
    assertThat(point.path("count").isNull()).isTrue();
    assertThat(point.path("insufficientData").asBoolean()).isTrue();
  }

  @Test
  void menuWorkflowNutritionOverrideAndAvailabilityAreRoleScoped() throws Exception {
    long dish = dish();
    String date =
        java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1).toString();
    long menu =
        call(
                "POST",
                "/sites/1/menus",
                Map.of(
                    "serviceDate",
                    date,
                    "mealType",
                    "LUNCH",
                    "items",
                    List.of(
                        Map.of(
                            "dishId",
                            dish,
                            "plannedQty",
                            20,
                            "servingWeightGrams",
                            "250.00",
                            "nutritionOverride",
                            Map.of("caloriesKcal", "400.00")))),
                "canteen",
                200)
            .path("id")
            .asLong();
    call("POST", "/sites/1/menus/" + menu + "/publish", Map.of(), "canteen", 403);
    call("POST", "/sites/1/menus/" + menu + "/submit", Map.of(), "canteen", 200);
    call("POST", "/sites/1/menus/" + menu + "/publish", Map.of(), "community", 200);
    call("GET", "/sites/2/menus?date=" + date, null, "canteen", 403);
    var result = call("GET", "/sites/1/menus?date=" + date, null, "canteen", 200);
    assertThat(result.path("items").get(0).path("nutritionOverride").path("caloriesKcal").asText())
        .isEqualTo("400.00");
  }

  @Test
  void forecastsRespectWeekdayFallbackMissingAndClosedDays() throws Exception {
    var day = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1);
    for (int i = 1; i <= 4; i++)
      jdbc.update(
          "INSERT INTO"
              + " attendance_daily(site_id,attendance_date,meal_type,diner_count)VALUES(1,?,'LUNCH',?)",
          day.minusWeeks(i),
          i * 10);
    assertThat(
            call(
                    "GET",
                    "/sites/1/forecasts?startDate=" + day + "&days=1&mealType=LUNCH",
                    null,
                    "canteen",
                    200)
                .path("points")
                .get(0)
                .path("count")
                .asInt())
        .isEqualTo(25);
    call(
        "GET",
        "/sites/2/forecasts?startDate=" + day + "&days=1&mealType=LUNCH",
        null,
        "canteen",
        403);
    jdbc.update(
        "INSERT INTO site_closed_day(site_id,closed_date,meal_type)VALUES(1,?,'LUNCH')", day);
    assertThat(
            call(
                    "GET",
                    "/sites/1/forecasts?startDate=" + day + "&days=1&mealType=LUNCH",
                    null,
                    "canteen",
                    200)
                .path("points")
                .get(0)
                .path("closed")
                .asBoolean())
        .isTrue();
  }

  @Test
  void recommendationFiltersAllergyAndIncompleteDataAndWeightsAreValidated() throws Exception {
    long dish = dish();
    long allergen =
        call("POST", "/allergens", Map.of("name", "测试过敏原"), "admin", 200).path("id").asLong();
    jdbc.update("INSERT INTO dish_allergen VALUES(?,?)", dish, allergen);
    jdbc.update("INSERT INTO elder(id,site_id,name)VALUES(1,1,'测试老人')");
    jdbc.update(
        "INSERT INTO family_binding(family_user_id,elder_id,status)VALUES(13,1,'APPROVED')");
    jdbc.update(
        "INSERT INTO"
            + " elder_health(elder_id,diseases_json,allergen_ids_json,allergen_data_complete,health_data_complete)VALUES(1,'[]',?,1,1)",
        "[" + allergen + "]");
    String day =
        java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1).toString();
    long menu =
        call(
                "POST",
                "/sites/1/menus",
                Map.of(
                    "serviceDate",
                    day,
                    "mealType",
                    "LUNCH",
                    "items",
                    List.of(Map.of("dishId", dish, "plannedQty", 20))),
                "canteen",
                200)
            .path("id")
            .asLong();
    call("POST", "/sites/1/menus/" + menu + "/submit", Map.of(), "canteen", 200);
    call("POST", "/sites/1/menus/" + menu + "/publish", Map.of(), "community", 200);
    var result =
        call(
            "GET",
            "/elders/1/recommendations?siteId=1&date=" + day + "&mealType=LUNCH",
            null,
            "family",
            200);
    assertThat(result.path("items").size()).isZero();
    assertThat(result.path("excluded").get(0).path("reason").asText()).contains("过敏");
    call(
        "PUT",
        "/recommendation-config",
        Map.of(
            "nutritionWeight",
            "0.9",
            "preferenceWeight",
            "0.9",
            "reviewWeight",
            "0",
            "historyWeight",
            "0"),
        "admin",
        400);
  }

  @Test
  void refreshingExposureAfterConfigChangeKeepsOneStableEvent() throws Exception {
    long dish = dish();
    long elder =
        call("POST", "/elders", Map.of("name", "刷新推荐老人", "siteId", 1), "community", 200)
            .path("id")
            .asLong();
    call(
        "PUT",
        "/elders/" + elder + "/health",
        Map.of("allergenDataComplete", true, "healthDataComplete", true),
        "community",
        200);
    String date =
        java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1).toString();
    long menu =
        call(
                "POST",
                "/sites/1/menus",
                Map.of(
                    "serviceDate",
                    date,
                    "mealType",
                    "LUNCH",
                    "items",
                    List.of(Map.of("dishId", dish, "plannedQty", 20))),
                "canteen",
                200)
            .path("id")
            .asLong();
    call("POST", "/sites/1/menus/" + menu + "/submit", Map.of(), "canteen", 200);
    call("POST", "/sites/1/menus/" + menu + "/publish", Map.of(), "community", 200);
    String path = "/elders/" + elder + "/recommendations?siteId=1&date=" + date + "&mealType=LUNCH";
    long first =
        call("GET", path, null, "community", 200)
            .path("items")
            .get(0)
            .path("recommendationEventId")
            .asLong();
    call(
        "PUT",
        "/recommendation-config",
        Map.of(
            "nutritionWeight",
            "1",
            "preferenceWeight",
            "0",
            "reviewWeight",
            "0",
            "historyWeight",
            "0"),
        "admin",
        200);
    var refreshed = call("GET", path, null, "community", 200);
    assertThat(refreshed.path("items").get(0).path("recommendationEventId").asLong())
        .isEqualTo(first);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM recommendation_event WHERE elder_id=?", Integer.class, elder))
        .isEqualTo(1);
  }

  private long dish() throws Exception {
    return call(
            "POST",
            "/dishes",
            Map.of(
                "name",
                "菜单测试菜",
                "originalPrice",
                "12.00",
                "elderPrice",
                "8.00",
                "ingredients",
                List.of("米饭"),
                "allergenDataComplete",
                true,
                "caloriesKcal",
                "300.00"),
            "admin",
            200)
        .path("id")
        .asLong();
  }
}
