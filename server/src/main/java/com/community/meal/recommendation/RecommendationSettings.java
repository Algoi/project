package com.community.meal.recommendation;

import static com.community.meal.common.Values.*;

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
public class RecommendationSettings {
  private final Db db;
  private final Access access;
  private final CatalogSnapshots catalog;

  public RecommendationSettings(Db db, Access access, CatalogSnapshots catalog) {
    this.db = db;
    this.access = access;
    this.catalog = catalog;
  }

  public Map<String, Object> currentConfig() {
    return db.one("SELECT * FROM recommendation_config ORDER BY version DESC,id DESC LIMIT 1");
  }

  public Map<String, Object> config(Actor p) {
    access.role(p, "ADMIN");
    return currentConfig();
  }

  public Map<String, Object> saveConfig(Actor p, Map<String, Object> in) {
    access.permit(p, "recommendation:write", "ADMIN");
    BigDecimal sum = BigDecimal.ZERO;
    var weights = new ArrayList<BigDecimal>();
    for (String key :
        List.of("nutritionWeight", "preferenceWeight", "reviewWeight", "historyWeight")) {
      BigDecimal value = decimal(in.get(key));
      if (value.signum() < 0 || value.scale() > 4) throw ApiException.invalid("权重须为非负数，最多四位小数");
      weights.add(value);
      sum = sum.add(value);
    }
    if (sum.compareTo(BigDecimal.ONE) != 0) throw ApiException.invalid("权重之和必须为 1");
    Map<String, Object> targets = in.get("targets") == null ? Map.of() : object(in.get("targets"));
    fields(targets, "caloriesKcal", "proteinGrams", "fatGrams", "carbGrams", "sodiumMg");
    for (Object target : targets.values()) {
      var interval = object(target);
      BigDecimal min = amount(interval.get("min")), max = amount(interval.get("max"));
      if (max.compareTo(min) < 0) throw ApiException.invalid("营养区间上限不能小于下限");
    }
    var old =
        db.one(
            "SELECT * FROM recommendation_config ORDER BY version DESC,id DESC LIMIT 1 FOR UPDATE");
    db.insert(
        "INSERT INTO"
            + " recommendation_config(nutrition_weight,preference_weight,review_weight,history_weight,targets_json,version,updated_by)VALUES(?,?,?,?,?,?,?)",
        weights.get(0),
        weights.get(1),
        weights.get(2),
        weights.get(3),
        db.json(targets),
        num(old.get("version")) + 1,
        p.userId());
    db.audit(p, "RECOMMENDATION_CONFIG", "CONFIG", num(old.get("version")) + 1);
    return currentConfig();
  }

  public List<Map<String, Object>> rules(Actor p) {
    access.role(p, "ADMIN");
    return db.list("SELECT * FROM diet_rule WHERE is_active=1 ORDER BY id");
  }

  public Map<String, Object> saveRule(Actor p, Long id, Map<String, Object> input) {
    access.permit(p, "recommendation:write", "ADMIN");
    String disease = text(input, "diseaseCode", 60, true),
        target = choice(input.get("targetType"), "DISH", "INGREDIENT"),
        value = text(input, "targetValue", 150, true);
    if (target.equals("DISH")) catalog.snapshot(Values.id(value));
    if (id == null)
      id =
          db.insert(
              "INSERT INTO"
                  + " diet_rule(disease_code,rule_type,target_type,target_value)VALUES(?,'PROHIBIT',?,?)",
              disease,
              target,
              value);
    else
      db.exec(
          "UPDATE diet_rule SET disease_code=?,rule_type='PROHIBIT',target_type=?,target_value=?"
              + " WHERE id=?",
          disease,
          target,
          value,
          id);
    db.audit(p, "DIET_RULE_SAVE", "DIET_RULE", id);
    return db.one("SELECT * FROM diet_rule WHERE id=?", id);
  }

  public void deleteRule(Actor p, long id) {
    access.permit(p, "recommendation:write", "ADMIN");
    db.exec("UPDATE diet_rule SET is_active=0 WHERE id=?", id);
    db.audit(p, "DIET_RULE_DISABLE", "DIET_RULE", id);
  }
}
