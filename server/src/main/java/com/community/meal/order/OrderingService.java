package com.community.meal.order;

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
public class OrderingService {
  private final Db db;
  private final Access access;
  private final ElderProfiles elders;
  private final MenuCatalog menus;
  private final DietSafety safety;
  private final BookingSites sites;
  private final RecommendationExposures exposures;
  private final OrderQueryService orders;

  public OrderingService(
      Db db,
      Access access,
      ElderProfiles elders,
      MenuCatalog menus,
      DietSafety safety,
      BookingSites sites,
      RecommendationExposures exposures,
      OrderQueryService orders) {
    this.db = db;
    this.access = access;
    this.elders = elders;
    this.menus = menus;
    this.safety = safety;
    this.sites = sites;
    this.exposures = exposures;
    this.orders = orders;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Map<String, Object> create(Actor p, Map<String, Object> input, String key) {
    access.permit(p, "orders:create", "FAMILY", "COMMUNITY");
    fields(
        input,
        "elderId",
        "siteId",
        "mealDate",
        "mealType",
        "deliveryType",
        "deliveryAddress",
        "contactName",
        "contactPhone",
        "items",
        "recommendationEventId");
    if (key != null && (key.isBlank() || key.length() > 80)) throw ApiException.invalid("幂等请求号无效");
    String hash = Hashes.sha256(db.json(input));
    if (key != null) {
      var old =
          db.optional(
              "SELECT * FROM meal_order WHERE buyer_user_id=? AND idempotency_key=?",
              p.userId(),
              key);
      if (old != null) {
        rule(hash.equals(old.get("requestHash")), "同一请求号不能提交不同订单");
        orders.authorize(p, old);
        return orders.view(old);
      }
    }
    long elderId = Values.id(input.get("elderId")), siteId = Values.id(input.get("siteId"));
    var elder = elders.rowForUpdate(elderId);
    access.elder(p, elder, true);
    rule(
        num(elder.get("siteId")) == siteId
            && str(elder.get("status")).equals("ACTIVE")
            && str(elder.get("approvalStatus")).equals("APPROVED"),
        "老人档案与网点不匹配或尚未生效");
    LocalDate date = Values.date(input.get("mealDate"));
    String meal = meal(input.get("mealType")),
        type = choice(input.get("deliveryType"), "DINE_IN", "TAKEOUT", "DELIVERY");
    var site = sites.site(siteId);
    rule(str(site.get("businessStatus")).equals("OPEN"), "网点暂停营业");
    rule(!sites.closedDates(siteId, date, date, meal).contains(date), "所选日期餐次停餐");
    if (site.get("enabledMeals") != null)
      rule(strings(site.get("enabledMeals")).contains(meal), "网点未开放此餐次");
    LocalDate today = LocalDate.now(BusinessTime.ZONE);
    rule(!date.isBefore(today) && !date.isAfter(today.plusDays(7)), "仅允许预订当天至未来 7 天");
    LocalTime opening =
        LocalTime.parse(str(site.get(meal.equals("LUNCH") ? "lunchTime" : "dinnerTime")));
    rule(
        ZonedDateTime.now(BusinessTime.ZONE)
            .isBefore(date.atTime(opening).atZone(BusinessTime.ZONE).minusMinutes(120)),
        "已超过本餐次订餐截止时间");
    for (var expired :
        db.list(
            "SELECT id FROM meal_order WHERE elder_id=? AND status='PENDING_PAYMENT' AND"
                + " payment_expires_at<=UTC_TIMESTAMP()",
            elderId))
      db.exec(
          "UPDATE meal_order SET status='CANCELLED' WHERE id=? AND status='PENDING_PAYMENT' AND"
              + " payment_expires_at<=UTC_TIMESTAMP()",
          expired.get("id"));
    if (db.count(
            "SELECT COUNT(*) FROM meal_order WHERE elder_id=? AND meal_date=? AND meal_type=? AND"
                + " status NOT IN('CANCELLED','REFUNDED')",
            elderId,
            date,
            meal)
        > 0)
      throw new ApiException(
          org.springframework.http.HttpStatus.CONFLICT, "CONFLICT", "该老人本日餐次已有有效订单");
    String address = text(input, "deliveryAddress", 255, type.equals("DELIVERY")),
        contact = text(input, "contactName", 80, type.equals("DELIVERY")),
        phone = text(input, "contactPhone", 30, type.equals("DELIVERY"));
    var entries = array(input.get("items"));
    rule(!entries.isEmpty() && entries.size() <= 30, "请选择 1 到 30 个菜品");
    List<Map<String, Object>> snapshots = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
    boolean subsidized = elders.subsidized(elderId,date);
    BigDecimal original = BigDecimal.ZERO, actual = BigDecimal.ZERO;
    var health = elders.health(p, elderId);
    List<Map<String, Object>> sorted =
        entries.stream()
            .map(Values::object)
            .sorted(Comparator.comparingLong(v -> Values.id(v.get("menuItemId"))))
            .toList();
    for (var entry : sorted) {
      long itemId = Values.id(entry.get("menuItemId"));
      rule(seen.add(itemId), "同一菜品请合并数量");
      int qty = integer(entry.get("quantity"), 1, 1000);
      var locked = menus.lockItem(itemId);
      rule(
          num(locked.get("siteId")) == siteId
              && str(locked.get("serviceDate")).equals(date.toString())
              && str(locked.get("mealType")).equals(meal)
              && str(locked.get("menuStatus")).equals("PUBLISHED")
              && str(locked.get("availability")).equals("AVAILABLE"),
          "餐品未发布、下架或日期网点不匹配");
      var item =
          menus.items(num(locked.get("menuId"))).stream()
              .filter(v -> num(v.get("id")) == itemId)
              .findFirst()
              .orElseThrow();
      rule(str(item.get("dishStatus")).equals("ACTIVE"), "菜品已停用");
      rule(qty <= num(item.get("plannedQty")) - menus.occupiedCurrent(itemId), "餐品剩余份数不足");
      String exclusion = safety.exclusion(health, item);
      rule(exclusion == null, exclusion == null ? "" : exclusion);
      BigDecimal price = amount(item.get("originalPrice")),
          effective = amount(item.get(subsidized ? "elderPrice" : "originalPrice"));
      original = original.add(price.multiply(BigDecimal.valueOf(qty)));
      actual = actual.add(effective.multiply(BigDecimal.valueOf(qty)));
      Long event =
          entry.get("recommendationEventId") == null
              ? null
              : Values.id(entry.get("recommendationEventId"));
      if (event != null) rule(exposures.belongs(event, elderId, itemId), "推荐记录不匹配");
      snapshots.add(
          map(
              "menuItemId",
              itemId,
              "dishId",
              item.get("dishId"),
              "name",
              item.get("dishName"),
              "qty",
              qty,
              "original",
              price,
              "actual",
              effective,
              "subsidy",
              price.subtract(effective),
              "nutrition",
              item.get("nutrition"),
              "event",
              event));
    }
    amount(original);
    amount(actual);
    String no = "MEAL" + UUID.randomUUID().toString().replace("-", "");
    long id =
        db.insert(
            "INSERT INTO"
                + " meal_order(order_no,elder_id,buyer_user_id,proxy_operator_id,site_id,meal_date,meal_type,delivery_type,delivery_address,contact_name,contact_phone,original_amount,subsidy_amount,payable_amount,payment_expires_at,idempotency_key,request_hash)VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL"
                + " 15 MINUTE),?,?)",
            no,
            elderId,
            p.userId(),
            (p.hasRole("COMMUNITY") && access.assignedSite(p, siteId)) ? p.userId() : null,
            siteId,
            date,
            meal,
            type,
            address,
            contact,
            phone,
            original,
            original.subtract(actual),
            actual,
            key,
            hash);
    for (var item : snapshots)
      db.exec(
          "INSERT INTO"
              + " meal_order_item(order_id,menu_item_id,dish_id,dish_name_snapshot,qty,original_unit_price,actual_unit_price,unit_subsidy,nutrition_snapshot_json,recommendation_event_id)VALUES(?,?,?,?,?,?,?,?,?,?)",
          id,
          item.get("menuItemId"),
          item.get("dishId"),
          item.get("name"),
          item.get("qty"),
          item.get("original"),
          item.get("actual"),
          item.get("subsidy"),
          db.json(item.get("nutrition")),
          item.get("event"));
    if (type.equals("DELIVERY")) db.exec("INSERT INTO delivery_record(order_id)VALUES(?)", id);
    db.audit(p, "ORDER_CREATE", "ORDER", id);
    return orders.view(orders.raw(id, false));
  }

  public void expire() {
    for (var expired :
        db.list(
            "SELECT id FROM meal_order WHERE status='PENDING_PAYMENT' AND"
                + " payment_expires_at<=UTC_TIMESTAMP() LIMIT 100"))
      db.exec(
          "UPDATE meal_order SET status='CANCELLED' WHERE id=? AND status='PENDING_PAYMENT' AND"
              + " payment_expires_at<=UTC_TIMESTAMP()",
          expired.get("id"));
  }
}
