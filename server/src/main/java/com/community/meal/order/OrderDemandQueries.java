package com.community.meal.order;

import static com.community.meal.common.Values.*;

import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class OrderDemandQueries implements OrderDemand {
  private final Db db;

  public OrderDemandQueries(Db db) {
    this.db = db;
  }

  public long occupied(long item, boolean locking) {
    String query =
        "SELECT i.qty FROM meal_order_item i JOIN meal_order o ON o.id=i.order_id WHERE"
            + " i.menu_item_id=? AND o.status NOT IN('CANCELLED','REFUNDED') AND"
            + " (o.status<>'PENDING_PAYMENT' OR o.payment_expires_at>UTC_TIMESTAMP())"
            + (locking ? " FOR UPDATE" : "");
    return db.list(query, item).stream().mapToLong(v -> num(v.get("qty"))).sum();
  }

  public long confirmed(long item) {
    return db.count(
        "SELECT COALESCE(SUM(i.qty),0) FROM meal_order_item i JOIN meal_order o ON o.id=i.order_id"
            + " WHERE i.menu_item_id=? AND o.status IN('PAID','PREPARING','READY','COMPLETED')",
        item);
  }

  public Map<String, Object> preparationHistory(long site, LocalDate day, String meal) {
    long total =
        db.count(
            "SELECT COALESCE(SUM(i.qty),0) FROM meal_order_item i JOIN meal_order o ON"
                + " o.id=i.order_id WHERE o.site_id=? AND o.meal_type=? AND o.status='COMPLETED'"
                + " AND o.meal_date>=? AND o.meal_date<?",
            site,
            meal,
            day.minusDays(56),
            day);
    long diners =
        db.count(
            "SELECT COUNT(DISTINCT elder_id,meal_date) FROM meal_order WHERE site_id=? AND"
                + " meal_type=? AND status='COMPLETED' AND meal_date>=? AND meal_date<?",
            site,
            meal,
            day.minusDays(56),
            day);
    long days =
        db.count(
            "SELECT COUNT(DISTINCT meal_date) FROM meal_order WHERE site_id=? AND meal_type=? AND"
                + " status='COMPLETED' AND meal_date>=? AND meal_date<?",
            site,
            meal,
            day.minusDays(56),
            day);
    return map("total", total, "diners", diners, "validDays", days);
  }

  public long historicalDishQty(long site, LocalDate day, String meal, long dish) {
    return db.count(
        "SELECT COALESCE(SUM(i.qty),0) FROM meal_order_item i JOIN meal_order o ON o.id=i.order_id"
            + " WHERE o.site_id=? AND o.meal_type=? AND i.dish_id=? AND o.status='COMPLETED' AND"
            + " o.meal_date>=? AND o.meal_date<?",
        site,
        meal,
        dish,
        day.minusDays(56),
        day);
  }

  public List<Map<String, Object>> attendance(
      long site, String meal, LocalDate from, LocalDate until) {
    return db.list(
        "SELECT attendance_date date,diner_count count FROM attendance_daily WHERE site_id=? AND"
            + " meal_type=? AND is_open=1 AND attendance_date>=? AND attendance_date<? ORDER BY"
            + " attendance_date DESC",
        site,
        meal,
        from,
        until);
  }
}
