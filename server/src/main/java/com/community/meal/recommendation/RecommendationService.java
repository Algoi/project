package com.community.meal.recommendation;

import static com.community.meal.common.Values.*;

import com.community.meal.algorithms.*;
import com.community.meal.auth.Access;
import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RecommendationService {
  private final Db db;
  private final Access access;
  private final ElderProfiles elders;
  private final MenuCatalog menus;
  private final DietSafety safety;
  private final RecommendationSettings settings;
  private final CatalogSnapshots catalog;
  private final OrderSignals orders;
  private final ReviewRatings reviews;
  private final AlgorithmSelection algorithms;

  public RecommendationService(
      Db db,
      Access access,
      ElderProfiles elders,
      MenuCatalog menus,
      DietSafety safety,
      RecommendationSettings settings,
      CatalogSnapshots catalog,
      OrderSignals orders,
      ReviewRatings reviews,
      AlgorithmSelection algorithms) {
    this.db = db;
    this.access = access;
    this.elders = elders;
    this.menus = menus;
    this.safety = safety;
    this.settings = settings;
    this.catalog = catalog;
    this.orders = orders;
    this.reviews = reviews;
    this.algorithms = algorithms;
  }

  public Map<String, Object> recommend(
      Actor actor, long elderId, long site, LocalDate day, String meal) {
    access.role(actor, "FAMILY", "COMMUNITY");
    var elder = elders.row(elderId);
    access.elder(actor, elder, true);
    rule(num(elder.get("siteId")) == site, "只能为老人所属网点订餐");
    rule(
        str(elder.get("status")).equals("ACTIVE")
            && str(elder.get("approvalStatus")).equals("APPROVED"),
        "老人档案尚未生效");
    var health = elders.health(actor, elderId);
    var config = settings.currentConfig();
    var menu = menus.get(actor, site, day, meal);
    List<Map<String, Object>> excluded = new ArrayList<>();
    if (!str(menu.get("status")).equals("PUBLISHED"))
      return map("items", List.of(), "excluded", excluded, "warning", "当天菜谱尚未发布");
    Map<Long, Map<String, Object>> eligible = new LinkedHashMap<>();
    List<RecommendationCandidate> candidates = new ArrayList<>();
    for (Object raw : array(menu.get("items"))) {
      var item = object(raw);
      if (!str(item.get("availability")).equals("AVAILABLE")
          || !str(item.get("dishStatus")).equals("ACTIVE")
          || num(item.get("remainingQty")) <= 0) continue;
      String reason = safety.exclusion(health, item);
      if (reason != null) {
        excluded.add(
            map("menuItemId", item.get("id"), "dishName", item.get("dishName"), "reason", reason));
        continue;
      }
      long id = num(item.get("id"));
      eligible.put(id, item);
      Map<String, BigDecimal> nutrition = new LinkedHashMap<>();
      for (var entry : object(item.get("nutrition")).entrySet())
        if (entry.getValue() != null) nutrition.put(entry.getKey(), decimal(entry.getValue()));
      String category = str(item.get("categoryId"));
      double preference =
          array(health.get("dislikedCategoryIds")).stream()
                  .map(Values::str)
                  .anyMatch(category::equals)
              ? 0
              : array(health.get("preferredCategoryIds")).stream()
                      .map(Values::str)
                      .anyMatch(category::equals)
                  ? 100
                  : 50;
      var average = reviews.averageForDish(num(item.get("dishId")));
      double rating = average == null ? 50 : (average.doubleValue() - 1) * 25;
      double history =
          orders.selectedDish(elderId, num(item.get("dishId")), day.minusDays(30))
              ? 100
              : orders.selectedAnyDish(
                      elderId, catalog.dishesInCategory(item.get("categoryId")), day.minusDays(30))
                  ? 70
                  : 50;
      candidates.add(
          new RecommendationCandidate(
              id, str(item.get("dishName")), nutrition, preference, rating, history));
    }
    Map<String, NutrientTarget> targets = new LinkedHashMap<>();
    if (config.get("targets") != null)
      for (var entry : object(config.get("targets")).entrySet()) {
        var value = object(entry.getValue());
        targets.put(
            entry.getKey(),
            new NutrientTarget(decimal(value.get("min")), decimal(value.get("max"))));
      }
    var parameters =
        new RecommendationParameters(
            decimal(config.get("nutritionWeight")),
            decimal(config.get("preferenceWeight")),
            decimal(config.get("reviewWeight")),
            decimal(config.get("historyWeight")),
            targets);
    var algorithm = algorithms.recommendation();
    List<RankedCandidate> ranked;
    try {ranked=algorithm.rank(List.copyOf(candidates),parameters);}
    catch(RuntimeException unavailable){
      boolean subsidy=elders.subsidized(elderId,day);
      List<Map<String,Object>> safe=new ArrayList<>();
      for(var item:eligible.values()){
        var row=new LinkedHashMap<>(item);row.put("menuItemId",item.get("id"));row.put("score",null);
        row.put("effectivePrice",item.get(subsidy?"elderPrice":"originalPrice"));
        row.put("reasons",List.of("通过已登记饮食规则核对","排序暂不可用，请人工选择"));safe.add(row);
      }
      return map("items",safe,"excluded",excluded,"warning","推荐排序暂不可用，当前展示通过饮食核对的可售菜品","degraded",true,"algorithm","safe_candidates_v1","configVersion",config.get("version"));
    }
    Set<Long> returned = new HashSet<>();
    for (var result : ranked)
      if (!eligible.containsKey(result.id()) || !returned.add(result.id()))
        throw new IllegalStateException("推荐算法返回了未通过安全校验或重复的餐品");
    if (returned.size() != eligible.size()) throw new IllegalStateException("推荐算法返回候选不完整");
    List<Map<String, Object>> items = new ArrayList<>();
    LocalDate exposureDate = LocalDate.now(BusinessTime.ZONE);
    boolean subsidy = elders.subsidized(elderId,day);
    for (var rankedItem : ranked) {
      var item = eligible.get(rankedItem.id());
      var signals = rankedItem.components();
      List<String> reasons = new ArrayList<>();
      reasons.add("通过已登记饮食规则核对");
      if (signals.preference() == 100) reasons.add("符合偏好分类");
      if (signals.review() >= 75) reasons.add("历史评价较高");
      if (signals.history() == 100) reasons.add("近期选择过此菜品");
      db.exec(
          "INSERT INTO"
              + " recommendation_event(elder_id,menu_item_id,score,reasons_json,event_date,config_version,algorithm)VALUES(?,?,?,?,?,?,?)"
              + " ON DUPLICATE KEY UPDATE"
              + " score=VALUES(score),reasons_json=VALUES(reasons_json),config_version=VALUES(config_version),algorithm=VALUES(algorithm)",
          elderId,
          item.get("id"),
          rankedItem.score(),
          db.json(reasons),
          exposureDate,
          config.get("version"),
          algorithm.id());
      long event =
          num(
              db.one(
                      "SELECT id FROM recommendation_event WHERE elder_id=? AND menu_item_id=? AND"
                          + " event_date=?",
                      elderId,
                      item.get("id"),
                      exposureDate)
                  .get("id"));
      var result = new LinkedHashMap<>(item);
      result.put("menuItemId", item.get("id"));
      result.put("score", rankedItem.score());
      result.put(
          "components",
          map(
              "nutrition",
              signals.nutrition(),
              "preference",
              signals.preference(),
              "review",
              signals.review(),
              "history",
              signals.history()));
      result.put("reasons", reasons);
      result.put("recommendationEventId", event);
      result.put("effectivePrice", item.get(subsidy ? "elderPrice" : "originalPrice"));
      items.add(result);
    }
    return map(
        "items",
        items,
        "excluded",
        excluded,
        "warning",
        !yes(health.get("healthDataComplete")) || !yes(health.get("allergenDataComplete"))
            ? "信息不完整，需人工确认"
            : null,
        "weights",map("nutrition",config.get("nutritionWeight"),"preference",config.get("preferenceWeight"),"review",config.get("reviewWeight"),"history",config.get("historyWeight")),
        "configVersion",
        config.get("version"),
        "algorithm",
        algorithm.id());
  }
}
