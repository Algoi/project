package com.community.meal.order;

import static com.community.meal.common.Values.*;

import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class OrderSignalQueries implements OrderSignals {
  private final Db db;

  public OrderSignalQueries(Db db) {
    this.db = db;
  }

  public List<Long> itemIdsForDish(long dish) {
    return db.list("SELECT id FROM meal_order_item WHERE dish_id=?", dish).stream()
        .map(v -> num(v.get("id")))
        .toList();
  }

  public boolean selectedDish(long elder, long dish, LocalDate since) {
    return selectedAnyDish(elder, List.of(dish), since);
  }

  public boolean selectedAnyDish(long elder, List<Long> dishes, LocalDate since) {
    if (dishes.isEmpty()) return false;
    List<Object> args = new ArrayList<>(List.of(elder, since));
    args.addAll(dishes);
    return db.count(
            "SELECT COUNT(*) FROM meal_order_item i JOIN meal_order o ON o.id=i.order_id WHERE"
                + " o.elder_id=? AND o.meal_date>=? AND o.status"
                + " IN('PAID','PREPARING','READY','COMPLETED') AND i.dish_id IN("
                + String.join(",", Collections.nCopies(dishes.size(), "?"))
                + ")",
            args.toArray())
        > 0;
  }

  public List<Map<String, Object>> nutritionRows(long elder, LocalDate start, LocalDate end) {
    return db.list(
        "SELECT o.id order_id,o.meal_date,i.qty,i.nutrition_snapshot_json FROM meal_order o JOIN"
            + " meal_order_item i ON i.order_id=o.id WHERE o.elder_id=? AND o.status='COMPLETED'"
            + " AND o.meal_date>=? AND o.meal_date<? ORDER BY o.meal_date,o.id",
        elder,
        start,
        end);
  }

  public List<Map<String, Object>> mealHistory(long elder) {
    return db.list(
        "SELECT"
            + " o.id,o.order_no,o.meal_date,o.meal_type,o.delivery_type,i.dish_name_snapshot,i.qty,i.nutrition_snapshot_json"
            + " FROM meal_order o JOIN meal_order_item i ON i.order_id=o.id WHERE o.elder_id=? AND"
            + " o.status='COMPLETED' ORDER BY o.meal_date DESC,o.id DESC",
        elder);
  }
}
