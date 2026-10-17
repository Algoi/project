package com.community.meal.review;

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
public class ReviewService {
  private final Db db;
  private final Access access;
  private final Orders orders;

  public ReviewService(Db db, Access access, Orders orders) {
    this.db = db;
    this.access = access;
    this.orders = orders;
  }

  private void files(Actor p, Object raw, String purpose) {
    for (long id : ids(raw))
      if (db.count(
              "SELECT COUNT(*) FROM stored_file WHERE id=? AND owner_user_id=? AND purpose=?",
              id,
              p.userId(),
              purpose)
          == 0) throw ApiException.forbidden();
  }

  public Map<String, Object> review(Actor p, Map<String, Object> input) {
    access.permit(p, "reviews:write", "FAMILY");
    int rating = integer(input.get("rating"), 1, 5);
    long item = id(input.get("orderItemId"));
    var row = db.one("SELECT * FROM meal_order_item WHERE id=?", item);
    var order = orders.raw(num(row.get("orderId")), false);
    orders.authorize(p, order);
    rule(str(order.get("status")).equals("COMPLETED"), "只有已核销或送达的菜品可以评价");
    files(p, input.get("imageFileIds"), "REVIEW");
    long id =
        db.insert(
            "INSERT INTO"
                + " dish_review(order_item_id,reviewer_user_id,rating,content,image_file_ids_json)VALUES(?,?,?,?,?)",
            item,
            p.userId(),
            rating,
            text(input, "content", 3000, false),
            db.json(ids(input.get("imageFileIds"))));
    db.audit(p, "REVIEW_CREATE", "REVIEW", id);
    return db.one("SELECT * FROM dish_review WHERE id=?", id);
  }

  public Map<String, Object> reviews(Actor p, Long site, int page, int size, Integer maxRating, String keyword) {
    access.role(p, "FAMILY", "CANTEEN", "COMMUNITY");
    List<Object> args = new ArrayList<>();
    String scope;
    if (site != null) {
      access.role(p, "CANTEEN", "COMMUNITY");
      access.site(p, site);
      scope = "o.site_id=?";
      args.add(site);
    } else {
      access.role(p, "FAMILY");
      scope = "r.reviewer_user_id=?";
      args.add(p.userId());
    }
    if (maxRating != null) {
      scope += " AND r.rating<=?";
      args.add(integer(maxRating, 1, 5));
    }
    scope += " AND (COALESCE(r.content,'') LIKE ? OR i.dish_name_snapshot LIKE ?)";
    args.add("%"+keyword.trim()+"%");args.add("%"+keyword.trim()+"%");
    return db.page(
        "SELECT r.*,i.dish_name_snapshot dish_name,o.site_id,o.meal_date FROM dish_review r JOIN"
            + " meal_order_item i ON i.id=r.order_item_id JOIN meal_order o ON o.id=i.order_id"
            + " WHERE "
            + scope
            + " ORDER BY r.id DESC",
        args,
        page,
        size);
  }
}
