package com.community.meal.ticket;

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
public class TicketService {
  private final Db db;
  private final Access access;
  private final Orders orders;

  public TicketService(Db db, Access access, Orders orders) {
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

  public Map<String, Object> createTicket(Actor p, Map<String, Object> input) {
    access.permit(p, "tickets:write", "FAMILY");
    long site = id(input.get("siteId"));
    db.one("SELECT id FROM service_site WHERE id=?", site);
    Long order = input.get("orderId") == null ? null : id(input.get("orderId"));
    if (order != null) {
      var row = orders.raw(order, false);
      orders.authorize(p, row);
      rule(num(row.get("siteId")) == site, "订单与工单网点不匹配");
    }
    files(p, input.get("imageFileIds"), "TICKET");
    long id =
        db.insert(
            "INSERT INTO"
                + " feedback_ticket(site_id,created_by,order_id,type,content,image_file_ids_json)VALUES(?,?,?,?,?,?)",
            site,
            p.userId(),
            order,
            choice(input.get("type"), "COMPLAINT", "FEEDBACK"),
            text(input, "content", 8000, true),
            db.json(ids(input.get("imageFileIds"))));
    db.audit(p, "TICKET_CREATE", "TICKET", id);
    return ticket(p, id);
  }

  private void ticketAccess(Actor p, Map<String, Object> row) {
    if (p.hasRole("FAMILY") && num(row.get("createdBy")) == p.userId()) return;
    if (p.hasRole("COMMUNITY")
        && db.count(
                "SELECT COUNT(*) FROM sys_user_site WHERE user_id=? AND site_id=?",
                p.userId(),
                row.get("siteId"))
            > 0) return;
    if (p.hasRole("ADMIN") && str(row.get("priority")).equals("MAJOR")) return;
    throw ApiException.forbidden();
  }

  public Map<String, Object> ticket(Actor p, long id) {
    var row =
        db.one(
            "SELECT *, (first_reply_at IS NULL AND created_at<DATE_SUB(UTC_TIMESTAMP(),INTERVAL 48"
                + " HOUR) AND status<>'RESOLVED') overdue FROM feedback_ticket WHERE id=?",
            id);
    ticketAccess(p, row);
    row.put(
        "replies",
        db.list(
            "SELECT r.*,u.real_name replier_name FROM ticket_reply r JOIN sys_user u ON"
                + " u.id=r.user_id WHERE r.ticket_id=? ORDER BY r.id",
            id));
    return row;
  }

  public Map<String, Object> tickets(Actor p, int page, int size, String keyword) {
    return tickets(p,page,size,keyword,"");
  }
  public Map<String,Object> tickets(Actor p,int page,int size,String keyword,String status) {return tickets(p,page,size,keyword,status,null);}
  public Map<String,Object> tickets(Actor p,int page,int size,String keyword,String status,Long siteId) {
    access.role(p, "FAMILY", "COMMUNITY", "ADMIN");
    List<Object> args = new ArrayList<>();
    List<String> scopes = new ArrayList<>();
    if (p.hasRole("FAMILY")) {
      scopes.add("t.created_by=?");
      args.add(p.userId());
    }
    if (p.hasRole("COMMUNITY")) {
      scopes.add("t.site_id IN(SELECT site_id FROM sys_user_site WHERE user_id=?)");
      args.add(p.userId());
    }
    if (p.hasRole("ADMIN")) scopes.add("t.priority='MAJOR'");
    args.add("%"+keyword.trim()+"%");args.add("%"+keyword.trim()+"%");
    args.add(status);args.add(status);args.add(siteId);args.add(siteId);
    return db.page(
        "SELECT t.*,s.name site_name,(t.first_reply_at IS NULL AND"
            + " t.created_at<DATE_SUB(UTC_TIMESTAMP(),INTERVAL 48 HOUR) AND t.status<>'RESOLVED')"
            + " overdue FROM feedback_ticket t JOIN service_site s ON s.id=t.site_id WHERE ("
            + String.join(" OR ", scopes)
            + ") AND (t.content LIKE ? OR s.name LIKE ?) AND (?='' OR t.status=?) AND (? IS NULL OR t.site_id=?) ORDER BY t.id DESC",
        args,
        page,
        size);
  }

  public Map<String, Object> reply(Actor p, long id, Map<String, Object> input) {
    access.permit(p, "tickets:write", "FAMILY", "COMMUNITY", "ADMIN");
    var row = ticket(p, id);
    rule(!str(row.get("status")).equals("RESOLVED"), "工单已办结");
    String content = text(input, "content", 8000, true);
    db.exec(
        "INSERT INTO ticket_reply(ticket_id,user_id,content)VALUES(?,?,?)",
        id,
        p.userId(),
        content);
    if (p.hasRole("COMMUNITY") || p.hasRole("ADMIN"))
      db.exec(
          "UPDATE feedback_ticket SET"
              + " first_reply_at=COALESCE(first_reply_at,UTC_TIMESTAMP()),status=IF(status='OPEN','IN_PROGRESS',status)"
              + " WHERE id=?",
          id);
    db.audit(p, "TICKET_REPLY", "TICKET", id);
    return ticket(p, id);
  }

  public Map<String, Object> ticketStatus(Actor p, long id, String status) {
    access.permit(p, "tickets:write", "COMMUNITY", "ADMIN");
    var row = ticket(p, id);
    String old = str(row.get("status"));
    choice(status, "IN_PROGRESS", "RESOLVED", "ESCALATED");
    rule(!old.equals("RESOLVED") && !old.equals(status), "工单状态不允许此操作");
    db.exec(
        "UPDATE feedback_ticket SET status=?,priority=IF(?='ESCALATED','MAJOR',priority) WHERE"
            + " id=?",
        status,
        status,
        id);
    db.audit(p, "TICKET_STATUS", "TICKET", id);
    return ticket(p, id);
  }
}
