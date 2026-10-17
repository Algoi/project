package com.community.meal.catalog;

import static com.community.meal.common.Values.*;

import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CatalogSnapshotQueries implements CatalogSnapshots {
  private final Db db;

  public CatalogSnapshotQueries(Db db) {
    this.db = db;
  }

  public Map<String, Object> snapshot(long id) {
    var row = db.one("SELECT * FROM dish WHERE id=?", id);
    row.put(
        "allergenIds",
        db.list("SELECT allergen_id FROM dish_allergen WHERE dish_id=?", id).stream()
            .map(v -> v.get("allergenId"))
            .toList());
    return row;
  }

  public List<Long> dishesInCategory(Object category) {
    return db.list("SELECT id FROM dish WHERE category_id=?", category).stream()
        .map(v -> num(v.get("id")))
        .toList();
  }

  public List<String> allergenNames(List<Long> ids) {
    if (ids.isEmpty()) return List.of();
    return db
        .list(
            "SELECT name FROM allergen WHERE id IN("
                + String.join(",", Collections.nCopies(ids.size(), "?"))
                + ") ORDER BY id",
            ids.toArray())
        .stream()
        .map(v -> str(v.get("name")))
        .toList();
  }
}
