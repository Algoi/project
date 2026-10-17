package com.community.meal.preparation;

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
public class PreparationService {
  private final Db db;
  private final Access access;
  private final MenuCatalog menus;
  private final Forecasts forecasts;
  private final OrderDemand demand;

  public PreparationService(
      Db db, Access access, MenuCatalog menus, Forecasts forecasts, OrderDemand demand) {
    this.db = db;
    this.access = access;
    this.menus = menus;
    this.forecasts = forecasts;
    this.demand = demand;
  }

  public Map<String, Object> preparation(Actor p, long site, LocalDate day, String meal) {
    access.role(p, "CANTEEN");
    access.site(p, site);
    var menu = menus.get(p, site, day, meal);
    Map<String,Object> forecast;boolean degraded=false;
    try {var points=array(forecasts.calculate(site,meal,day,1).get("points"));forecast=object(points.get(0));}
    catch(AlgorithmUnavailableException unavailable){forecast=map("count",null,"insufficientData",true);degraded=true;}
    var stats = demand.preparationHistory(site, day, meal);
    long total = num(stats.get("total")),
        diners = num(stats.get("diners")),
        validDays = num(stats.get("validDays"));
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Object v : array(menu.get("items"))) {
      var item = object(v);
      long confirmed = demand.confirmed(num(item.get("id")));
      long historical = demand.historicalDishQty(site, day, meal, num(item.get("dishId")));
      long suggested = confirmed;
      if (validDays >= 7 && diners > 0 && total > 0 && forecast.get("count") != null)
        suggested =
            Math.max(
                confirmed,
                Math.round(
                    num(forecast.get("count")) * (double) total / diners * historical / total));
      var adjustment =
          db.optional(
              "SELECT adjusted_qty FROM preparation_item WHERE menu_item_id=?", item.get("id"));
      rows.add(
          map(
              "menuItemId",
              item.get("id"),
              "dishName",
              item.get("dishName"),
              "confirmedQty",
              confirmed,
              "forecastCount",
              forecast.get("count"),
              "suggestedQty",
              suggested,
              "adjustedQty",
              adjustment == null ? null : adjustment.get("adjustedQty")));
    }
    return map(
        "items",
        rows,
        "forecast",
        forecast,
        "warning",
        degraded?"预测暂不可用，建议采用已确认份数":validDays < 7 ? "历史不足 7 个有效营业日，菜品建议采用已确认份数" : null);
  }

  @Transactional
  public Map<String, Object> adjust(
      Actor p, long site, LocalDate day, String meal, Map<String, Object> input) {
    access.permit(p, "preparation:write", "CANTEEN");
    access.site(p, site);
    for (Object v : array(input.get("items"))) {
      var item = object(v);
      long id = Values.id(item.get("menuItemId"));
      var selected = menus.get(p, site, day, meal);
      rule(
          array(selected.get("items")).stream()
              .map(Values::object)
              .anyMatch(vv -> num(vv.get("id")) == id),
          "备餐餐品不属于所选网点日期餐次");
      int qty = integer(item.get("adjustedQty"), 0, 100000);
      db.exec(
          "INSERT INTO preparation_item(menu_item_id,adjusted_qty,updated_by)VALUES(?,?,?) ON"
              + " DUPLICATE KEY UPDATE"
              + " adjusted_qty=VALUES(adjusted_qty),updated_by=VALUES(updated_by)",
          id,
          qty,
          p.userId());
    }
    db.audit(p, "PREPARATION_ADJUST", "SITE", site);
    return preparation(p, site, day, meal);
  }
}
