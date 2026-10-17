package com.community.meal;

import static org.assertj.core.api.Assertions.*;

import com.community.meal.algorithms.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;

@SpringBootTest(
    properties = {
      "app.algorithms.forecast=recent_weighted_average_v1",
      "app.algorithms.recommendation=fixture_ranker_v1"
    })
@Import(AlgorithmReplacementIntegrationTest.PluginConfiguration.class)
class AlgorithmReplacementIntegrationTest extends BusinessTestSupport {
  @Autowired FixtureRanking ranking;

  @BeforeEach
  void resetPlugin() {
    ranking.forgedId = null;
    ranking.fail = false;
    ranking.seen = List.of();
  }

  @Test
  void configurationSelectsAnotherForecastAndPersistsItsIdentity() throws Exception {
    LocalDate target = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1);
    for (int i = 0; i < 3; i++)
      jdbc.update(
          "INSERT INTO"
              + " attendance_daily(site_id,attendance_date,meal_type,diner_count)VALUES(1,?,'LUNCH',?)",
          target.minusDays(2 + i),
          30 - i * 10);
    var result =
        call(
            "GET",
            "/sites/1/forecasts?startDate=" + target + "&days=1&mealType=LUNCH",
            null,
            "canteen",
            200);
    assertThat(result.path("algorithm").asText()).isEqualTo("recent_weighted_average_v1");
    assertThat(result.path("points").get(0).path("count").asInt()).isEqualTo(23);
    assertThat(
            jdbc.queryForObject(
                "SELECT algorithm FROM forecast_result ORDER BY id DESC LIMIT 1", String.class))
        .isEqualTo("recent_weighted_average_v1");
  }

  @Test
  void replacementReceivesOnlySafeCandidatesAndCannotReturnExcludedItems() throws Exception {
    LocalDate date = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1);
    long safe = dish("安全候选"), unsafe = dish("含过敏原候选");
    long allergen =
        call("POST", "/allergens", Map.of("name", "替换算法测试过敏原"), "admin", 200).path("id").asLong();
    jdbc.update("INSERT INTO dish_allergen VALUES(?,?)", unsafe, allergen);
    jdbc.update("INSERT INTO elder(id,site_id,name)VALUES(1,1,'替换算法测试老人')");
    jdbc.update(
        "INSERT INTO family_binding(family_user_id,elder_id,status)VALUES(13,1,'APPROVED')");
    jdbc.update(
        "INSERT INTO"
            + " elder_health(elder_id,diseases_json,allergen_ids_json,allergen_data_complete,health_data_complete)VALUES(1,'[]',?,1,1)",
        "[" + allergen + "]");
    long menu =
        call(
                "POST",
                "/sites/1/menus",
                Map.of(
                    "serviceDate",
                    date.toString(),
                    "mealType",
                    "LUNCH",
                    "items",
                    List.of(
                        Map.of("dishId", safe, "plannedQty", 20),
                        Map.of("dishId", unsafe, "plannedQty", 20))),
                "canteen",
                200)
            .path("id")
            .asLong();
    call("POST", "/sites/1/menus/" + menu + "/submit", Map.of(), "canteen", 200);
    call("POST", "/sites/1/menus/" + menu + "/publish", Map.of(), "community", 200);
    String path = "/elders/1/recommendations?siteId=1&date=" + date + "&mealType=LUNCH";
    var response = call("GET", path, null, "family", 200);
    assertThat(ranking.seen).hasSize(1);
    assertThat(ranking.seen.get(0).name()).isEqualTo("安全候选");
    assertThat(response.path("items")).hasSize(1);
    assertThat(response.path("excluded")).hasSize(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT algorithm FROM recommendation_event WHERE elder_id=1", String.class))
        .isEqualTo("fixture_ranker_v1");
    long unsafeItem =
        jdbc.queryForObject(
            "SELECT id FROM daily_menu_item WHERE menu_id=? AND dish_id=?",
            Long.class,
            menu,
            unsafe);
    ranking.fail=true;
    var degraded=call("GET",path,null,"family",200);
    assertThat(degraded.path("degraded").asBoolean()).isTrue();
    assertThat(degraded.path("items")).hasSize(1);
    assertThat(degraded.path("items").get(0).path("dishName").asText()).isEqualTo("安全候选");
    assertThat(degraded.path("items").get(0).path("score").isNull()).isTrue();
    ranking.fail=false;
    ranking.forgedId = unsafeItem;
    call("GET", path, null, "family", 500);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM recommendation_event WHERE menu_item_id=?",
                Integer.class,
                unsafeItem))
        .isZero();
    call(
        "POST",
        "/orders",
        Map.of(
            "elderId",
            1,
            "siteId",
            1,
            "mealDate",
            date.toString(),
            "mealType",
            "LUNCH",
            "deliveryType",
            "TAKEOUT",
            "items",
            List.of(Map.of("menuItemId", unsafeItem, "quantity", 1))),
        "family",
        422);
    assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM meal_order WHERE elder_id=1", Integer.class))
        .isZero();
  }

  private long dish(String name) throws Exception {
    return call(
            "POST",
            "/dishes",
            Map.of(
                "name",
                name,
                "originalPrice",
                "12.00",
                "elderPrice",
                "8.00",
                "ingredients",
                List.of("米饭"),
                "allergenDataComplete",
                true),
            "admin",
            200)
        .path("id")
        .asLong();
  }

  @TestConfiguration
  static class PluginConfiguration {
    @Bean
    FixtureRanking replacementRanking() {
      return new FixtureRanking();
    }
  }

  static class FixtureRanking implements RecommendationAlgorithm {
    Long forgedId;
    boolean fail;
    List<RecommendationCandidate> seen = List.of();

    public String id() {
      return "fixture_ranker_v1";
    }

    public List<RankedCandidate> rank(
        List<RecommendationCandidate> candidates, RecommendationParameters params) {
      seen = List.copyOf(candidates);
      if(fail)throw new IllegalStateException("测试排序故障");
      if (forgedId != null)
        return List.of(
            new RankedCandidate(forgedId, BigDecimal.TEN, new ScoreComponents(50, 50, 50, 50)));
      return candidates.stream()
          .map(
              c -> new RankedCandidate(c.id(), BigDecimal.TEN, new ScoreComponents(50, 50, 50, 50)))
          .toList();
    }
  }
}
