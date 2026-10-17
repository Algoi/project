package com.community.meal.review;

import static com.community.meal.common.Values.*;

import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ReviewRatingQueries implements ReviewRatings {
  private final Db db;
  private final OrderSignals orders;

  public ReviewRatingQueries(Db db, OrderSignals orders) {
    this.db = db;
    this.orders = orders;
  }

  public BigDecimal averageForDish(long dish) {
    var items = orders.itemIdsForDish(dish);
    if (items.isEmpty()) return null;
    Object value =
        db.one(
                "SELECT AVG(rating) average FROM dish_review WHERE order_item_id IN("
                    + String.join(",", Collections.nCopies(items.size(), "?"))
                    + ")",
                items.toArray())
            .get("average");
    return value == null ? null : decimal(value);
  }
}
