package com.community.meal.catalog;

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
public class CatalogService {
  private final Db db;
  private final Access access;

  public CatalogService(Db db, Access access) {
    this.db = db;
    this.access = access;
  }

  private void admin(Actor p, String operation) {
    access.permit(p, operation, "ADMIN");
  }

  public List<Map<String, Object>> dictionary(Actor p, String type) {
    access.role(p, "ADMIN", "CANTEEN", "COMMUNITY", "FAMILY");
    String table = choice(type, "dish_category", "allergen");
    return db.list("SELECT * FROM " + table + " ORDER BY id");
  }

  public Map<String, Object> saveDictionary(
      Actor p, String type, Long id, Map<String, Object> input) {
    admin(p, "dishes:write");
    String table = choice(type, "dish_category", "allergen");
    String name = text(input, "name", 80, true);
    if (id == null) id = db.insert("INSERT INTO " + table + "(name) VALUES(?)", name);
    else {
      db.one("SELECT id FROM " + table + " WHERE id=?", id);
      db.exec("UPDATE " + table + " SET name=? WHERE id=?", name, id);
      if (table.equals("allergen") && input.containsKey("isActive"))
        db.exec("UPDATE allergen SET is_active=? WHERE id=?", yes(input.get("isActive")), id);
    }
    db.audit(p, "DICTIONARY_SAVE", table, id);
    return db.one("SELECT * FROM " + table + " WHERE id=?", id);
  }

  public void deleteDictionary(Actor p, String type, long id) {
    admin(p, "dishes:write");
    if (type.equals("allergen")) db.exec("UPDATE allergen SET is_active=0 WHERE id=?", id);
    else db.exec("DELETE FROM dish_category WHERE id=?", id);
    db.audit(p, "DICTIONARY_DISABLE", type, id);
  }

  public Map<String, Object> dishes(Actor p, int page, int size, String keyword) {
    access.role(p, "ADMIN", "CANTEEN", "COMMUNITY", "FAMILY");
    var result =
        db.page(
            "SELECT d.*,c.name category_name FROM dish d LEFT JOIN dish_category c ON"
                + " c.id=d.category_id WHERE d.name LIKE ? ORDER BY d.id DESC",
            List.of("%" + keyword + "%"),
            page,
            size);
    return result;
  }

  public Map<String, Object> dish(Actor p, long id) {
    access.role(p, "ADMIN", "CANTEEN", "COMMUNITY", "FAMILY");
    var row = db.one("SELECT * FROM dish WHERE id=?", id);
    row.put(
        "allergenIds",
        db
            .list("SELECT allergen_id FROM dish_allergen WHERE dish_id=? ORDER BY allergen_id", id)
            .stream()
            .map(r -> r.get("allergenId"))
            .toList());
    return row;
  }

  public Map<String, Object> saveDish(Actor p, Long id, Map<String, Object> input) {
    admin(p, "dishes:write");
    fields(
        input,
        "name",
        "categoryId",
        "description",
        "imageFileId",
        "ingredients",
        "weightGrams",
        "originalPrice",
        "elderPrice",
        "caloriesKcal",
        "proteinGrams",
        "fatGrams",
        "carbGrams",
        "sodiumMg",
        "allergenIds",
        "allergenDataComplete");
    String name = text(input, "name", 120, true);
    var original = amount(input.get("originalPrice"));
    var elder = amount(input.get("elderPrice"));
    if (elder.compareTo(original) > 0) throw ApiException.invalid("补贴价不得高于原价");
    Long category = input.get("categoryId") == null ? null : Values.id(input.get("categoryId"));
    if (category != null) db.one("SELECT id FROM dish_category WHERE id=?", category);
    if (input.get("imageFileId") != null)
      db.one(
          "SELECT id FROM stored_file WHERE id=? AND purpose='DISH_IMAGE'",
          Values.id(input.get("imageFileId")));
    var allergens = ids(input.get("allergenIds"));
    for (long allergen : allergens)
      db.one("SELECT id FROM allergen WHERE id=? AND is_active=1", allergen);
    List<Object> args =
        new ArrayList<>(
            Arrays.asList(
                name,
                category,
                text(input, "description", 3000, false),
                input.get("imageFileId"),
                db.json(strings(input.get("ingredients"))),
                input.containsKey("weightGrams") ? amount(input.get("weightGrams")) : null,
                original,
                elder));
    for (String field :
        List.of("caloriesKcal", "proteinGrams", "fatGrams", "carbGrams", "sodiumMg"))
      args.add(input.get(field) == null ? null : amount(input.get(field)));
    args.add(yes(input.get("allergenDataComplete")));
    if (id == null)
      id =
          db.insert(
              "INSERT INTO"
                  + " dish(name,category_id,description,image_file_id,ingredients_json,weight_grams,original_price,elder_price,calories_kcal,protein_grams,fat_grams,carb_grams,sodium_mg,allergen_data_complete)"
                  + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
              args.toArray());
    else {
      db.one("SELECT id FROM dish WHERE id=?", id);
      args.add(id);
      db.exec(
          "UPDATE dish SET"
              + " name=?,category_id=?,description=?,image_file_id=?,ingredients_json=?,weight_grams=?,original_price=?,elder_price=?,calories_kcal=?,protein_grams=?,fat_grams=?,carb_grams=?,sodium_mg=?,allergen_data_complete=?"
              + " WHERE id=?",
          args.toArray());
    }
    db.exec("DELETE FROM dish_allergen WHERE dish_id=?", id);
    for (long allergen : allergens) db.exec("INSERT INTO dish_allergen VALUES(?,?)", id, allergen);
    db.audit(p, "DISH_SAVE", "DISH", id);
    return dish(p, id);
  }

  public Map<String, Object> statusDish(Actor p, long id, Map<String, Object> input) {
    admin(p, "dishes:write");
    dish(p, id);
    db.exec(
        "UPDATE dish SET status=? WHERE id=?",
        choice(input.get("status"), "ACTIVE", "DISABLED"),
        id);
    db.audit(p, "DISH_STATUS", "DISH", id);
    return dish(p, id);
  }
}
