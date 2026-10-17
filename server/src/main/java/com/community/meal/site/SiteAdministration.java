package com.community.meal.site;

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
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class SiteAdministration {
  private final Db db;
  private final Access access;

  public SiteAdministration(Db db, Access access) {
    this.db = db;
    this.access = access;
  }

  private void admin(Actor p, String operation) {
    access.permit(p, operation, "ADMIN");
  }

  public Map<String, Object> saveSite(Actor p, Long id, Map<String, Object> input) {
    boolean creating = id == null;
    admin(p, "sites:write");
    fields(
        input,
        "name",
        "address",
        "phone",
        "serviceArea",
        "managerUserId",
        "openingHours",
        "lunchTime",
        "dinnerTime",
        "enabledMeals");
    String name = text(input, "name", 120, true),
        address = text(input, "address", 255, false),
        phone = text(input, "phone", 30, false),
        area = text(input, "serviceArea", 2000, false),
        hours = text(input, "openingHours", 100, false);
    Long manager =
        input.get("managerUserId") == null ? null : Values.id(input.get("managerUserId"));
    if (manager != null) db.one("SELECT id FROM sys_user WHERE id=?", manager);
    String lunch = str(input.getOrDefault("lunchTime", "11:30")),
        dinner = str(input.getOrDefault("dinnerTime", "17:30"));
    try {
      java.time.LocalTime.parse(lunch);
      java.time.LocalTime.parse(dinner);
    } catch (Exception e) {
      throw ApiException.invalid("开餐时间无效");
    }
    var meals = strings(input.getOrDefault("enabledMeals", List.of("LUNCH", "DINNER")));
    for (String m : meals) meal(m);
    rule(!meals.isEmpty(), "至少开启一个餐次");
    if (id == null)
      id =
          db.insert(
              "INSERT INTO"
                  + " service_site(name,address,phone,service_area,manager_user_id,opening_hours,lunch_time,dinner_time,enabled_meals_json)"
                  + " VALUES(?,?,?,?,?,?,?,?,?)",
              name,
              address,
              phone,
              area,
              manager,
              hours,
              lunch,
              dinner,
              db.json(meals));
    else {
      db.one("SELECT id FROM service_site WHERE id=?", id);
      db.exec(
          "UPDATE service_site SET"
              + " name=?,address=?,phone=?,service_area=?,manager_user_id=?,opening_hours=?,lunch_time=?,dinner_time=?,enabled_meals_json=?"
              + " WHERE id=?",
          name,
          address,
          phone,
          area,
          manager,
          hours,
          lunch,
          dinner,
          db.json(meals),
          id);
    }
    db.audit(p, "SITE_" + (creating ? "CREATE" : "UPDATE"), "SITE", id);
    return siteDetails(p, id);
  }

  public Map<String, Object> siteDetails(Actor p, long id) {
    access.site(p, id);
    return db.one("SELECT * FROM service_site WHERE id=?", id);
  }

  public Map<String, Object> statusSite(Actor p, long id, Map<String, Object> input) {
    admin(p, "sites:write");
    db.one("SELECT id FROM service_site WHERE id=?", id);
    db.exec(
        "UPDATE service_site SET business_status=? WHERE id=?",
        choice(input.get("status"), "OPEN", "CLOSED"),
        id);
    db.audit(p, "SITE_STATUS", "SITE", id);
    return siteDetails(p, id);
  }

  public List<Map<String, Object>> closedDates(Actor p, long id) {
    access.site(p, id);
    return db.list(
        "SELECT closed_date,meal_type,reason FROM site_closed_day WHERE site_id=? ORDER BY"
            + " closed_date,meal_type",
        id);
  }

  public List<Map<String, Object>> saveClosedDates(Actor p, long id, Map<String, Object> input) {
    admin(p, "sites:write");
    siteDetails(p, id);
    var dates = array(input.get("dates"));
    for (Object v : dates) {
      var d = object(v);
      date(d.get("date"));
      meal(d.get("mealType"));
      text(d, "reason", 200, false);
    }
    db.exec("DELETE FROM site_closed_day WHERE site_id=?", id);
    for (Object v : dates) {
      var d = object(v);
      db.exec(
          "INSERT INTO site_closed_day(site_id,closed_date,meal_type,reason) VALUES(?,?,?,?)",
          id,
          date(d.get("date")),
          meal(d.get("mealType")),
          text(d, "reason", 200, false));
    }
    db.audit(p, "SITE_CALENDAR", "SITE", id);
    return closedDates(p, id);
  }
}
