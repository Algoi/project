package com.community.meal.site;

import static com.community.meal.common.Values.*;

import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class BookingSiteQueries implements BookingSites {
  private final Db db;

  public BookingSiteQueries(Db db) {
    this.db = db;
  }

  public Map<String, Object> site(long id) {
    return db.one("SELECT * FROM service_site WHERE id=?", id);
  }

  public Set<LocalDate> closedDates(long site, LocalDate from, LocalDate to, String meal) {
    Set<LocalDate> dates = new HashSet<>();
    for (var row :
        db.list(
            "SELECT closed_date FROM site_closed_day WHERE site_id=? AND meal_type=? AND"
                + " closed_date BETWEEN ? AND ?",
            site,
            meal,
            from,
            to)) dates.add(date(row.get("closedDate")));
    return Set.copyOf(dates);
  }
}
