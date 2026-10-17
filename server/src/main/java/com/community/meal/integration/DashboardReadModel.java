package com.community.meal.integration;

import static com.community.meal.common.Values.*;

import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Cross-domain reporting projection for the current shared SQL database. */
@Component
public class DashboardReadModel implements DashboardReports {
  private final Db db;
  private final Forecasts forecasts;

  public DashboardReadModel(Db db, Forecasts forecasts) {
    this.db = db;
    this.forecasts = forecasts;
  }

  public Object read(String type, LocalDate from, LocalDate to, Long site) {
    List<Object> args = new ArrayList<>(List.of(from, to));
    String scope = "o.meal_date BETWEEN ? AND ?";
    if (site != null) {
      scope += " AND o.site_id=?";
      args.add(site);
    }
    if (type.equals("orders-trend"))
      return db.list(
          "SELECT o.meal_date date,COUNT(*) order_count,SUM(o.status NOT"
              + " IN('CANCELLED','REFUNDED')) valid_count FROM meal_order o WHERE "
              + scope
              + " GROUP BY o.meal_date ORDER BY o.meal_date",
          args.toArray());
    if (type.equals("overview") || type.equals("revenue-subsidy")) {
      var base =
          db.one(
              "SELECT COUNT(*) order_count,COUNT(DISTINCT"
                  + " IF(o.status='COMPLETED',CONCAT(o.elder_id,':',o.meal_date,':',o.meal_type),NULL))"
                  + " diner_count FROM meal_order o WHERE "
                  + scope,
              args.toArray());
      var payments =
          db.one(
              "SELECT COALESCE(SUM(p.amount),0) paid FROM payment_record p JOIN meal_order o ON"
                  + " o.id=p.order_id WHERE p.status='SUCCESS' AND "
                  + scope,
              args.toArray());
      var refunds =
          db.one(
              "SELECT COALESCE(SUM(r.amount),0) refunded FROM refund_record r JOIN meal_order o ON"
                  + " o.id=r.order_id WHERE r.status='REFUNDED' AND "
                  + scope,
              args.toArray());
      var subsidies =
          db.one(
              "SELECT COALESCE(SUM(l.amount),0) subsidy FROM subsidy_ledger l JOIN meal_order o ON"
                  + " o.id=l.order_id WHERE "
                  + scope,
              args.toArray());
      base.put(
          "netRevenue",
          decimal(payments.get("paid")).subtract(decimal(refunds.get("refunded"))).setScale(2));
      base.put("subsidyAmount", decimal(subsidies.get("subsidy")).setScale(2));
      base.put("refundAmount", decimal(refunds.get("refunded")).setScale(2));
      base.put("scopeNotice", "按供餐日期统计；收入为成功支付减成功退款，补贴为账本净额");
      return base;
    }
    if (type.equals("recommendation-stats")) {
      List<Object> recArgs = new ArrayList<>(List.of(from, to));
      String recScope = "r.event_date BETWEEN ? AND ?";
      if (site != null) {
        recScope += " AND m.site_id=?";
        recArgs.add(site);
      }
      long exposure =
          db.count(
              "SELECT COUNT(*) FROM recommendation_event r JOIN daily_menu_item i ON"
                  + " i.id=r.menu_item_id JOIN daily_menu m ON m.id=i.menu_id WHERE "
                  + recScope,
              recArgs.toArray());
      long conversions =
          db.count(
              "SELECT COUNT(DISTINCT r.id) FROM recommendation_event r JOIN daily_menu_item i ON"
                  + " i.id=r.menu_item_id JOIN daily_menu m ON m.id=i.menu_id JOIN meal_order_item"
                  + " oi ON oi.recommendation_event_id=r.id JOIN meal_order o ON o.id=oi.order_id"
                  + " WHERE o.status IN('PAID','PREPARING','READY','COMPLETED') AND "
                  + recScope,
              recArgs.toArray());
      return map(
          "exposureCount",
          exposure,
          "conversionCount",
          conversions,
          "conversionRate",
          exposure == 0
              ? null
              : BigDecimal.valueOf(conversions)
                  .divide(BigDecimal.valueOf(exposure), 4, RoundingMode.HALF_UP),
          "notice",
          "曝光按老人、每日菜品、展示日期去重；转化为该曝光关联的有效已支付订单，退款不计转化");
    }
    if (type.equals("nutrition-stats")) {
      var rows =
          db.list(
              "SELECT o.meal_date,i.qty,i.nutrition_snapshot_json FROM meal_order o JOIN"
                  + " meal_order_item i ON i.order_id=o.id WHERE o.status='COMPLETED' AND "
                  + scope
                  + " ORDER BY o.meal_date",
              args.toArray());
      Map<String, BigDecimal> totals = new LinkedHashMap<>();
      for (var row : rows) {
        if (row.get("nutritionSnapshot") == null) continue;
        var nutrition = object(row.get("nutritionSnapshot"));
        for (String metric : NutritionMetrics.NUTRIENTS)
          if (nutrition.get(metric) != null)
            totals.merge(
                metric,
                decimal(nutrition.get(metric)).multiply(BigDecimal.valueOf(num(row.get("qty")))),
                BigDecimal::add);
      }
      return map("totals", totals, "empty", rows.isEmpty(), "notice", "只汇总已核销或送达餐食的营养快照，不提供个人健康诊断");
    }
    if (type.equals("attendance-forecast")) {
      List<Map<String, Object>> rows = new ArrayList<>();
      for (var selected :
          db.list(
              "SELECT id,name FROM service_site" + (site == null ? "" : " WHERE id=?"),
              site == null ? new Object[] {} : new Object[] {site})) {
        rows.add(
            map(
                "siteId",
                selected.get("id"),
                "siteName",
                selected.get("name"),
                "forecast",
                forecasts.calculate(
                    num(selected.get("id")),
                    "LUNCH",
                    LocalDate.now(com.community.meal.common.BusinessTime.ZONE).plusDays(1),
                    7)));
      }
      return rows;
    }
    throw ApiException.notFound();
  }
}
