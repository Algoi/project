package com.community.meal.menu;

import static com.community.meal.common.Values.*;

import com.community.meal.auth.Access;
import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MenuService implements MenuCatalog {
  private final Db db;
  private final Access access;
  private final CatalogSnapshots catalog;
  private final OrderDemand demand;
  private final OrderExceptions exceptions;

  public MenuService(
      Db db,
      Access access,
      CatalogSnapshots catalog,
      OrderDemand demand,
      OrderExceptions exceptions) {
    this.db = db;
    this.access = access;
    this.catalog = catalog;
    this.demand = demand;
    this.exceptions = exceptions;
  }

  public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  public List<Map<String, Object>> items(long id) {
    var rows = db.list("SELECT * FROM daily_menu_item WHERE menu_id=? ORDER BY id", id);
    for (var row : rows) {
      var dish = catalog.snapshot(num(row.get("dishId")));
      for (String field :
          List.of(
              "imageFileId",
              "categoryId",
              "ingredients",
              "originalPrice",
              "elderPrice",
              "caloriesKcal",
              "proteinGrams",
              "fatGrams",
              "carbGrams",
              "sodiumMg",
              "weightGrams")) row.put(field, dish.get(field));
      row.put("dishName", dish.get("name"));
      row.put("dishStatus", dish.get("status"));
      row.put("dishAllergenComplete", dish.get("allergenDataComplete"));
      row.put(
          "allergenIds",
          row.get("allergenIdsOverride") != null
              ? row.get("allergenIdsOverride")
              : dish.get("allergenIds"));
      if (row.get("ingredientsOverride") != null)
        row.put("ingredients", row.get("ingredientsOverride"));
      row.put(
          "allergenDataComplete",
          row.get("allergenDataComplete") == null
              ? yes(dish.get("allergenDataComplete"))
              : yes(row.get("allergenDataComplete")));
      row.put("nutrition", nutrition(row));
      row.put(
          "remainingQty",
          Math.max(0, num(row.get("plannedQty")) - demand.occupied(num(row.get("id")), false)));
    }
    return rows;
  }

  public Map<String, Object> lockItem(long item) {
    return db.one(
        "SELECT i.*,m.site_id,m.service_date,m.meal_type,m.status menu_status FROM daily_menu_item"
            + " i JOIN daily_menu m ON m.id=i.menu_id WHERE i.id=? FOR UPDATE",
        item);
  }

  public Map<String, Object> nutrition(Map<String, Object> item) {
    var result = new LinkedHashMap<String, Object>();
    Map<String, Object> override =
        item.get("nutritionOverride") instanceof Map<?, ?>
            ? object(item.get("nutritionOverride"))
            : Map.of();
    for (String key : List.of("caloriesKcal", "proteinGrams", "fatGrams", "carbGrams", "sodiumMg"))
      result.put(key, override.getOrDefault(key, item.get(key)));
    return result;
  }

  public long occupiedCurrent(long item) {
    return demand.occupied(item, true);
  }

  public Map<String, Object> get(Actor p, long site, LocalDate date, String meal) {
    access.role(p, "ADMIN", "CANTEEN", "COMMUNITY", "FAMILY");
    boolean staff = p.hasRole("ADMIN") || (p.isStaff() && access.assignedSite(p, site));
    if (!staff && !p.hasRole("FAMILY")) throw ApiException.forbidden();
    var row =
        db.optional(
            "SELECT * FROM daily_menu WHERE site_id=? AND service_date=? AND meal_type=?",
            site,
            date,
            meal);
    if (row == null || (!staff && !str(row.get("status")).equals("PUBLISHED")))
      return map(
          "items", List.of(), "siteId", site, "serviceDate", date.toString(), "mealType", meal);
    row.put("items", items(num(row.get("id"))));
    return row;
  }

  public Map<String, Object> save(Actor p, long site, Long id, Map<String, Object> input) {
    access.permit(p, "menus:write", "CANTEEN");
    access.site(p, site);
    LocalDate day = date(input.get("serviceDate"));
    rule(!day.isBefore(LocalDate.now(ZONE)), "不能维护过去的菜谱");
    String meal = meal(input.get("mealType"));
    var entries = array(input.get("items"));
    rule(!entries.isEmpty(), "至少选择一个菜品");
    if (id == null)
      id =
          db.insert(
              "INSERT INTO daily_menu(site_id,service_date,meal_type)VALUES(?,?,?)",
              site,
              day,
              meal);
    else {
      var old = db.one("SELECT * FROM daily_menu WHERE id=? FOR UPDATE", id);
      rule(num(old.get("siteId")) == site, "菜谱网点不匹配");
      rule(
          str(old.get("serviceDate")).equals(day.toString())
              && str(old.get("mealType")).equals(meal),
          "不能更换既有菜谱的日期或餐次");
    }
    Set<Long> retained = new HashSet<>();
    for (Object entry : entries) {
      var item = object(entry);
      long dish = Values.id(item.get("dishId"));
      if (!str(catalog.snapshot(dish).get("status")).equals("ACTIVE"))
        throw ApiException.notFound();
      int qty = integer(item.get("plannedQty"), 0, 100000);
      var old =
          db.optional(
              "SELECT * FROM daily_menu_item WHERE menu_id=? AND dish_id=? FOR UPDATE", id, dish);
      if (old != null) rule(qty >= occupiedCurrent(num(old.get("id"))), "可售数量不能少于已占用份数");
      Object weight =
          item.get("servingWeightGrams") == null ? null : amount(item.get("servingWeightGrams"));
      Map<String, Object> nutrition =
          item.get("nutritionOverride") == null ? Map.of() : object(item.get("nutritionOverride"));
      fields(nutrition, "caloriesKcal", "proteinGrams", "fatGrams", "carbGrams", "sodiumMg");
      nutrition.values().forEach(Values::amount);
      String allergens =
          item.containsKey("allergenIds") ? db.json(ids(item.get("allergenIds"))) : null;
      String ingredients =
          item.containsKey("ingredients") ? db.json(strings(item.get("ingredients"))) : null;
      Object complete =
          item.containsKey("allergenDataComplete") ? yes(item.get("allergenDataComplete")) : null;
      long itemId;
      if (old == null)
        itemId =
            db.insert(
                "INSERT INTO"
                    + " daily_menu_item(menu_id,dish_id,planned_qty,serving_weight_grams,nutrition_override_json,allergen_ids_override_json,ingredients_override_json,allergen_data_complete)VALUES(?,?,?,?,?,?,?,?)",
                id,
                dish,
                qty,
                weight,
                db.json(nutrition),
                allergens,
                ingredients,
                complete);
      else {
        itemId = num(old.get("id"));
        db.exec(
            "UPDATE daily_menu_item SET"
                + " planned_qty=?,serving_weight_grams=?,nutrition_override_json=?,allergen_ids_override_json=?,ingredients_override_json=?,allergen_data_complete=?"
                + " WHERE id=?",
            qty,
            weight,
            db.json(nutrition),
            allergens,
            ingredients,
            complete,
            itemId);
      }
      rule(retained.add(itemId), "同一菜品不能重复添加");
    }
    for (var old : db.list("SELECT id FROM daily_menu_item WHERE menu_id=?", id))
      if (!retained.contains(num(old.get("id"))))
        db.exec("UPDATE daily_menu_item SET availability='OFF_SHELF' WHERE id=?", old.get("id"));
    db.exec("UPDATE daily_menu SET status='DRAFT' WHERE id=?", id);
    db.audit(p, "MENU_SAVE", "MENU", id);
    return get(p, site, day, meal);
  }

  public Map<String, Object> transition(Actor p, long site, long id, boolean publish) {
    access.permit(p, publish ? "menus:publish" : "menus:write", publish ? "COMMUNITY" : "CANTEEN");
    access.site(p, site);
    var row = db.one("SELECT * FROM daily_menu WHERE id=? FOR UPDATE", id);
    rule(num(row.get("siteId")) == site, "菜谱网点不匹配");
    rule(str(row.get("status")).equals(publish ? "SUBMITTED" : "DRAFT"), "菜谱状态不允许此操作");
    if (publish)
      db.exec(
          "UPDATE daily_menu SET status='PUBLISHED',published_by=?,published_at=UTC_TIMESTAMP()"
              + " WHERE id=?",
          p.userId(),
          id);
    else
      db.exec(
          "UPDATE daily_menu SET status='SUBMITTED',submitted_by=?,submitted_at=UTC_TIMESTAMP()"
              + " WHERE id=?",
          p.userId(),
          id);
    db.audit(p, publish ? "MENU_PUBLISH" : "MENU_SUBMIT", "MENU", id);
    return get(p, site, date(row.get("serviceDate")), str(row.get("mealType")));
  }

  public Map<String, Object> availability(Actor p, long site, long id, Map<String, Object> input) {
    access.permit(p, "menus:publish", "COMMUNITY");
    access.site(p, site);
    var item =
        db.one(
            "SELECT mi.id,m.site_id FROM daily_menu_item mi JOIN daily_menu m ON m.id=mi.menu_id"
                + " WHERE mi.id=?",
            id);
    rule(num(item.get("siteId")) == site, "餐品网点不匹配");
    String state = choice(input.get("availability"), "AVAILABLE", "OFF_SHELF");
    db.exec("UPDATE daily_menu_item SET availability=? WHERE id=?", state, id);
    if (state.equals("OFF_SHELF")) exceptions.menuItemUnavailable(id);
    db.audit(p, "MENU_AVAILABILITY", "MENU_ITEM", id);
    return item;
  }
  private Map<String,Object> copyEntry(Map<String,Object> item){
    Map<String,Object> result=new LinkedHashMap<>();for(String key:List.of("dishId","plannedQty","servingWeightGrams","nutritionOverride","allergenDataComplete"))if(item.get(key)!=null)result.put(key,item.get(key));
    if(item.get("allergenIdsOverride")!=null)result.put("allergenIds",item.get("allergenIdsOverride"));if(item.get("ingredientsOverride")!=null)result.put("ingredients",item.get("ingredientsOverride"));return result;
  }
  private List<Map<String,Object>> copyItems(long menu){return db.list("SELECT * FROM daily_menu_item WHERE menu_id=? AND availability='AVAILABLE' ORDER BY id",menu).stream().map(this::copyEntry).toList();}
  private Map<String,Object> createDraft(Actor p,long site,LocalDate day,String meal,Object entries){
    if(db.count("SELECT COUNT(*) FROM daily_menu WHERE site_id=? AND service_date=? AND meal_type=?",site,day,meal)>0)throw new ApiException(org.springframework.http.HttpStatus.CONFLICT,"CONFLICT","目标日期餐次已存在菜谱，请在原菜谱维护");
    return save(p,site,null,map("serviceDate",day.toString(),"mealType",meal,"items",entries));
  }
  public Map<String,Object> copy(Actor p,long site,long source,Map<String,Object> in){
    access.permit(p,"menus:write","CANTEEN");access.site(p,site);var old=db.one("SELECT * FROM daily_menu WHERE id=?",source);rule(num(old.get("siteId"))==site,"菜谱网点不匹配");
    var result=createDraft(p,site,date(in.get("serviceDate")),meal(in.get("mealType")),copyItems(source));db.audit(p,"MENU_COPY","MENU",result.get("id"));return result;
  }
  private LocalDate week(Object value){LocalDate day=date(value);rule(day.getDayOfWeek()==DayOfWeek.MONDAY,"周模板起始日期应为周一");return day;}
  public Map<String,Object> templates(Actor p,long site,int page,int size){access.role(p,"CANTEEN");access.site(p,site);return db.page("SELECT id,name,created_at FROM menu_template WHERE site_id=? ORDER BY id DESC",List.of(site),page,size);}
  public Map<String,Object> saveTemplate(Actor p,long site,Map<String,Object> in){
    access.permit(p,"menus:write","CANTEEN");access.site(p,site);LocalDate start=week(in.get("weekStart"));String name=text(in,"name",100,true);List<Map<String,Object>> entries=new ArrayList<>();
    for(var menu:db.list("SELECT * FROM daily_menu WHERE site_id=? AND service_date BETWEEN ? AND ? ORDER BY service_date,meal_type",site,start,start.plusDays(6))){var items=copyItems(num(menu.get("id")));if(!items.isEmpty())entries.add(map("dayOffset",java.time.temporal.ChronoUnit.DAYS.between(start,date(menu.get("serviceDate"))),"mealType",menu.get("mealType"),"items",items));}
    rule(!entries.isEmpty(),"该周没有可保存的菜谱");long id=db.insert("INSERT INTO menu_template(site_id,name,entries_json,created_by) VALUES(?,?,?,?)",site,name,db.json(entries),p.userId());db.audit(p,"MENU_TEMPLATE_SAVE","MENU_TEMPLATE",id);return map("id",id,"name",name,"entries",entries);
  }
  public Map<String,Object> applyTemplate(Actor p,long site,long id,Map<String,Object> in){
    access.permit(p,"menus:write","CANTEEN");access.site(p,site);var template=db.one("SELECT * FROM menu_template WHERE id=? AND site_id=?",id,site);LocalDate start=week(in.get("weekStart"));List<Map<String,Object>> menus=new ArrayList<>();
    for(Object value:array(template.get("entries"))){var entry=object(value);menus.add(createDraft(p,site,start.plusDays(integer(entry.get("dayOffset"),0,6)),meal(entry.get("mealType")),entry.get("items")));}
    db.audit(p,"MENU_TEMPLATE_APPLY","MENU_TEMPLATE",id);return map("menus",menus);
  }
  public Map<String,Object> deleteTemplate(Actor p,long site,long id){access.permit(p,"menus:write","CANTEEN");access.site(p,site);db.one("SELECT id FROM menu_template WHERE id=? AND site_id=?",id,site);db.exec("DELETE FROM menu_template WHERE id=?",id);db.audit(p,"MENU_TEMPLATE_DELETE","MENU_TEMPLATE",id);return map("id",id);}

}
