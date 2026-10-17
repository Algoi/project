package com.community.meal.integration;

import com.community.meal.common.*;
import com.community.meal.contracts.*;
import org.springframework.stereotype.Component;

/**
 * Shared-database read adapter; replace this adapter if resource owners move to separate stores.
 */
@Component
public class PrivateFileAuthorization implements FileReadAccess {
  private final Db db;

  public PrivateFileAuthorization(Db db) {
    this.db = db;
  }

  public boolean mayRead(Actor p, long id, String purpose) {
    boolean allowed = false;
    if (purpose.equals("DISH_IMAGE"))
      allowed = db.count("SELECT COUNT(*) FROM dish WHERE image_file_id=?", id) > 0;
    if (p.hasRole("COMMUNITY")) {
      if (purpose.equals("BINDING"))
        allowed =
            db.count(
                    "SELECT COUNT(*) FROM family_binding b JOIN elder e ON e.id=b.elder_id JOIN"
                        + " sys_user_site s ON s.site_id=e.site_id WHERE b.proof_file_id=? AND"
                        + " s.user_id=?",
                    id,
                    p.userId())
                > 0;
      if (purpose.equals("SUBSIDY"))
        allowed =
            db.count(
                    "SELECT COUNT(*) FROM subsidy_application a JOIN elder e ON e.id=a.elder_id"
                        + " JOIN sys_user_site s ON s.site_id=e.site_id WHERE"
                        + " JSON_CONTAINS(a.proof_file_ids_json,CAST(? AS JSON)) AND s.user_id=?",
                    Long.toString(id),
                    p.userId())
                > 0;
    }
    if (purpose.equals("REVIEW"))
      allowed =
          db.count(
                  "SELECT COUNT(*) FROM dish_review r JOIN meal_order_item i ON"
                      + " i.id=r.order_item_id JOIN meal_order o ON o.id=i.order_id WHERE"
                      + " JSON_CONTAINS(r.image_file_ids_json,CAST(? AS JSON)) AND"
                      + " (o.buyer_user_id=? OR (? AND o.site_id IN(SELECT site_id FROM"
                      + " sys_user_site WHERE user_id=?)))",
                  Long.toString(id),
                  p.userId(),
                  p.isStaff(),
                  p.userId())
              > 0;
    if (purpose.equals("TICKET"))
      allowed =
          db.count(
                  "SELECT COUNT(*) FROM feedback_ticket t WHERE"
                      + " JSON_CONTAINS(t.image_file_ids_json,CAST(? AS JSON)) AND (t.created_by=?"
                      + " OR (? AND t.site_id IN(SELECT site_id FROM sys_user_site WHERE"
                      + " user_id=?)) OR (? AND t.priority='MAJOR'))",
                  Long.toString(id),
                  p.userId(),
                  p.hasRole("COMMUNITY"),
                  p.userId(),
                  p.hasRole("ADMIN"))
              > 0;
    return allowed;
  }
}
