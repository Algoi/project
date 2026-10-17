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
public class OrderQueryService implements Orders {
  private final Db db;
  private final Access access;
  private final ElderProfiles elders;
  private final BookingSites sites;

  public OrderQueryService(Db db, Access access, ElderProfiles elders, BookingSites sites) {
    this.db = db;
    this.access = access;
    this.elders = elders;
    this.sites = sites;
  }

  public Map<String, Object> raw(long id, boolean lock) {
    return db.one("SELECT * FROM meal_order WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
  }

  public void authorize(Actor p, Map<String, Object> order) {
    access.role(p, "FAMILY", "COMMUNITY", "CANTEEN");
    boolean staff = p.isStaff() && access.assignedSite(p, num(order.get("siteId")));
    boolean family =
        access.linkedFamily(p, num(order.get("elderId")))
            && (num(order.get("buyerUserId")) == p.userId()
                || order.get("proxyOperatorId") != null);
    if (!staff && !family) throw ApiException.forbidden();
  }

  public Map<String, Object> get(Actor p, long id) {
    var order = raw(id, false);
    authorize(p, order);
    return view(order);
  }

  public Map<String, Object> view(Map<String, Object> order) {
    var result = new LinkedHashMap<>(order);
    result.put("orderId", order.get("id"));
    result.remove("pickupCodeHash");
    result.remove("pickupCipher");
    result.remove("requestHash");
    result.remove("idempotencyKey");
    result.remove("activeSlot");
    result.put(
        "items",
        db.list("SELECT * FROM meal_order_item WHERE order_id=? ORDER BY id", order.get("id")));
    result.put("elderName", elders.row(num(order.get("elderId"))).get("name"));
    result.put("siteName", sites.site(num(order.get("siteId"))).get("name"));
    var delivery = db.optional("SELECT * FROM delivery_record WHERE order_id=?", order.get("id"));
    result.put("delivery", delivery);
    result.put("events",db.list("SELECT event_type,label,created_at FROM order_event WHERE order_id=? ORDER BY id",order.get("id")));
    return result;
  }

  public Map<String, Object> list(
      Actor p, int page, int size, String status, Long site, String date) {
    return list(p,page,size,status,site,date,"");
  }
  public Map<String,Object> list(Actor p,int page,int size,String status,Long site,String date,String keyword){return list(p,page,size,status,site,date,keyword,null,"","","");}
  public Map<String,Object> list(Actor p,int page,int size,String status,Long site,String date,String keyword,Long elderId,String mealType,String dateFrom,String dateTo){
    access.role(p, "FAMILY", "COMMUNITY", "CANTEEN");
    List<Object> args = new ArrayList<>();
    List<String> scopes = new ArrayList<>();
    if (p.isStaff()) {
      scopes.add("o.site_id IN(SELECT site_id FROM sys_user_site WHERE user_id=?)");
      args.add(p.userId());
    }
    if (p.hasRole("FAMILY")) {
      scopes.add(
          "((o.buyer_user_id=? OR o.proxy_operator_id IS NOT NULL) AND o.elder_id IN(SELECT"
              + " elder_id FROM family_binding WHERE family_user_id=? AND status='APPROVED'))");
      args.add(p.userId());
      args.add(p.userId());
    }
    String scope = "(" + String.join(" OR ", scopes) + ")";
    if (!status.isBlank()) {
      scope += " AND o.status=?";
      args.add(status);
    }
    if (site != null) {
      if (!(p.isStaff() && access.assignedSite(p, site))
          && !(p.hasRole("FAMILY")
              && db.count(
                      "SELECT COUNT(*) FROM family_binding b JOIN elder e ON e.id=b.elder_id WHERE"
                          + " b.family_user_id=? AND b.status='APPROVED' AND e.site_id=?",
                      p.userId(),
                      site)
                  > 0)) throw ApiException.forbidden();
      scope += " AND o.site_id=?";
      args.add(site);
    }
    if (!date.isBlank()) {
      scope += " AND o.meal_date=?";
      args.add(Values.date(date));
    }
    if(elderId!=null){scope+=" AND o.elder_id=?";args.add(elderId);}
    if(!mealType.isBlank()){scope+=" AND o.meal_type=?";args.add(Values.meal(mealType));}
    if(!dateFrom.isBlank()){scope+=" AND o.meal_date>=?";args.add(Values.date(dateFrom));}
    if(!dateTo.isBlank()){scope+=" AND o.meal_date<=?";args.add(Values.date(dateTo));}
    if(!dateFrom.isBlank()&&!dateTo.isBlank()&&Values.date(dateFrom).isAfter(Values.date(dateTo)))throw ApiException.invalid("开始日期不能晚于结束日期");
    scope+=" AND (o.order_no LIKE ? OR e.name LIKE ?)";args.add("%"+keyword.trim()+"%");args.add("%"+keyword.trim()+"%");
    return db.page(
        "SELECT"
            + " o.id,o.order_no,o.elder_id,o.site_id,o.meal_date,o.meal_type,o.delivery_type,o.status,o.original_amount,o.subsidy_amount,o.payable_amount,o.exception_flag,o.exception_note,o.created_at,e.name"
            + " elder_name,s.name site_name FROM meal_order o JOIN elder e ON e.id=o.elder_id JOIN"
            + " service_site s ON s.id=o.site_id WHERE "
            + scope
            + " ORDER BY o.id DESC",
        args,
        page,
        size);
  }

  public Map<String, Object> exception(Actor p, long id, Map<String, Object> input) {
    access.role(p, "COMMUNITY");
    var order = raw(id, true);
    authorize(p, order);
    db.exec(
        "UPDATE meal_order SET exception_flag=?,exception_note=? WHERE id=?",
        yes(input.get("exceptionFlag")),
        text(input, "exceptionNote", 500, true),
        id);
    db.audit(p, "ORDER_EXCEPTION", "ORDER", id);
    return view(raw(id, false));
  }
}
