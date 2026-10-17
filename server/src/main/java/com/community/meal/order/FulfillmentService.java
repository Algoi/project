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
public class FulfillmentService {
  private final Db db;
  private final Access access;
  private final OrderQueryService orders;
  private final ElderProfiles elders;
  private final Crypto crypto;

  public FulfillmentService(
      Db db, Access access, OrderQueryService orders, ElderProfiles elders, Crypto crypto) {
    this.db = db;
    this.access = access;
    this.orders = orders;
    this.elders = elders;
    this.crypto = crypto;
  }

  public Map<String, Object> prepare(Actor p, long id, boolean ready) {
    access.permit(p, "orders:prepare", "CANTEEN");
    var order = orders.raw(id, true);
    orders.authorize(p, order);
    rule(str(order.get("status")).equals(ready ? "PREPARING" : "PAID"), "订单状态不允许此出餐操作");
    db.exec("UPDATE meal_order SET status=? WHERE id=?", ready ? "READY" : "PREPARING", id);
    db.audit(p, ready ? "ORDER_READY" : "ORDER_PREPARING", "ORDER", id);
    return orders.view(orders.raw(id, false));
  }

  public Map<String, Object> voucher(Actor p, long id) {
    access.role(p, "FAMILY", "COMMUNITY");
    var order = orders.raw(id, false);
    orders.authorize(p, order);
    rule(!str(order.get("deliveryType")).equals("DELIVERY"), "配送订单无需现场取餐凭证");
    rule(List.of("PAID", "PREPARING", "READY").contains(str(order.get("status"))), "当前状态无法获取取餐凭证");
    return map(
        "orderId",
        id,
        "orderNo",
        order.get("orderNo"),
        "voucher",
        crypto.decrypt(str(order.get("pickupCipher"))));
  }

  public Map<String, Object> redemption(Actor p, Map<String, Object> input, boolean confirm) {
    access.permit(p, "redemptions:write", "CANTEEN", "COMMUNITY");
    Map<String, Object> found;
    String voucher = text(input, "voucher", 100, false);
    if (voucher != null && !voucher.isBlank())
      found = db.one("SELECT * FROM meal_order WHERE pickup_code_hash=?", Hashes.sha256(voucher));
    else {
      String orderNo = text(input, "orderNo", 64, true);
      found = db.one("SELECT * FROM meal_order WHERE order_no=?", orderNo);
      rule(
          str(elders.row(num(found.get("elderId"))).get("name"))
              .equals(text(input, "elderName", 80, true)),
          "老人身份核验不匹配");
    }
    var order = orders.raw(num(found.get("id")), true);
    orders.authorize(p, order);
    rule(!str(order.get("deliveryType")).equals("DELIVERY"), "配送订单应通过送达确认完成");
    rule(
        str(order.get("status")).equals("READY"),
        str(order.get("status")).equals("COMPLETED") ? "订单已经核销" : "订单尚未出餐");
    if (confirm) {
      db.exec(
          "INSERT INTO redemption(order_id,verifier_user_id,verify_method)VALUES(?,?,?)",
          order.get("id"),
          p.userId(),
          voucher == null ? "MANUAL" : "VOUCHER");
      db.exec("UPDATE meal_order SET status='COMPLETED' WHERE id=?", order.get("id"));
      attendance(
          num(order.get("siteId")), Values.date(order.get("mealDate")), str(order.get("mealType")));
      db.audit(p, "REDEMPTION", "ORDER", order.get("id"));
    }
    return orders.view(orders.raw(num(order.get("id")), false));
  }

  public void attendance(long site, LocalDate date, String meal) {
    long count =
        db.count(
            "SELECT COUNT(DISTINCT elder_id) FROM meal_order WHERE site_id=? AND meal_date=? AND"
                + " meal_type=? AND status='COMPLETED'",
            site,
            date,
            meal);
    db.exec(
        "INSERT INTO"
            + " attendance_daily(site_id,attendance_date,meal_type,diner_count,is_open)VALUES(?,?,?,?,1)"
            + " ON DUPLICATE KEY UPDATE diner_count=VALUES(diner_count)",
        site,
        date,
        meal,
        count);
  }

  public List<Map<String, Object>> deliveryList(Actor p, long site, LocalDate date, String meal) {
    access.role(p, "CANTEEN", "COMMUNITY");
    access.site(p, site);
    var orders =
        db.list(
            "SELECT"
                + " o.id,o.order_no,o.elder_id,o.delivery_address,o.contact_name,o.contact_phone,o.status,d.status"
                + " delivery_status,e.name elder_name FROM meal_order o JOIN delivery_record d ON"
                + " d.order_id=o.id JOIN elder e ON e.id=o.elder_id WHERE o.site_id=? AND"
                + " o.meal_date=? AND o.meal_type=? AND o.delivery_type='DELIVERY' AND o.status"
                + " IN('PAID','PREPARING','READY','COMPLETED') ORDER BY o.id",
            site,
            date,
            meal);
    for (var order : orders) {
      order.put(
          "items",
          db.list(
              "SELECT dish_name_snapshot,qty FROM meal_order_item WHERE order_id=?",
              order.get("id")));
      order.put("dietaryNotes", elders.dietarySummary(num(order.get("elderId"))));
    }
    return orders;
  }

  public Map<String, Object> delivery(Actor p, long id, String status) {
    access.permit(p, "delivery:write", "CANTEEN", "COMMUNITY");
    var order = orders.raw(id, true);
    orders.authorize(p, order);
    rule(
        str(order.get("deliveryType")).equals("DELIVERY")
            && str(order.get("status")).equals("READY"),
        "订单不是待交付的配送订单");
    var delivery = db.one("SELECT * FROM delivery_record WHERE order_id=? FOR UPDATE", id);
    if (status.equals("OUT_FOR_DELIVERY"))
      rule(str(delivery.get("status")).equals("PENDING"), "配送状态不允许出发");
    else {
      choice(status, "DELIVERED");
      rule(str(delivery.get("status")).equals("OUT_FOR_DELIVERY"), "需先确认正在配送");
    }
    db.exec(
        "UPDATE delivery_record SET"
            + " status=?,updated_by=?,delivered_at=IF(?='DELIVERED',UTC_TIMESTAMP(),NULL) WHERE"
            + " order_id=?",
        status,
        p.userId(),
        status,
        id);
    if (status.equals("DELIVERED")) {
      db.exec("UPDATE meal_order SET status='COMPLETED' WHERE id=?", id);
      attendance(
          num(order.get("siteId")), Values.date(order.get("mealDate")), str(order.get("mealType")));
    }
    db.audit(p, "DELIVERY_STATUS", "ORDER", id);
    return orders.view(orders.raw(id, false));
  }
}
