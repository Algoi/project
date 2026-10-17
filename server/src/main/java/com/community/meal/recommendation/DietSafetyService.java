package com.community.meal.recommendation;

import static com.community.meal.common.Values.*;

import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DietSafetyService implements DietSafety {
  private final Db db;

  public DietSafetyService(Db db) {
    this.db = db;
  }

  public String exclusion(Map<String, Object> health, Map<String, Object> item) {
    if (!yes(health.get("healthDataComplete"))
        || !yes(health.get("allergenDataComplete"))
        || !yes(item.get("allergenDataComplete"))
        || array(item.get("ingredients")).isEmpty()) return "资料不足，需人工确认";
    Set<String> allergies =
        new HashSet<>(array(health.get("allergenIds")).stream().map(Values::str).toList());
    for (Object allergen : array(item.get("allergenIds")))
      if (allergies.contains(str(allergen))) return "匹配已登记过敏原";
    var ingredients =
        array(item.get("ingredients")).stream().map(v -> str(v).toLowerCase(Locale.ROOT)).toList();
    for (Object avoid : array(health.get("avoidIngredientNames")))
      if (ingredients.contains(str(avoid).toLowerCase(Locale.ROOT))) return "匹配已登记禁忌食材";
    for (Object disease : array(health.get("diseaseCodes")))
      for (var rule :
          db.list("SELECT * FROM diet_rule WHERE disease_code=? AND is_active=1", str(disease))) {
        if ((str(rule.get("targetType")).equals("DISH")
                && str(rule.get("targetValue")).equals(str(item.get("dishId"))))
            || (str(rule.get("targetType")).equals("INGREDIENT")
                && ingredients.contains(str(rule.get("targetValue")).toLowerCase(Locale.ROOT))))
          return "匹配已生效的饮食禁忌规则";
      }
    return null;
  }
}
