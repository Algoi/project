package com.community.meal.notice;

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
public class NoticeService {
  private final Db db;
  private final Access access;

  public NoticeService(Db db, Access access) {
    this.db = db;
    this.access = access;
  }

  private boolean noticeVisible(Actor p, Map<String, Object> row) {
    if (p.hasRole("ADMIN")) return true;
    if (!str(row.get("status")).equals("PUBLISHED")) return false;
    if (row.get("expireAt") != null
        && !Instant.parse(str(row.get("expireAt"))).isAfter(Instant.now())) return false;
    if (row.get("publishAt") != null
        && Instant.parse(str(row.get("publishAt"))).isAfter(Instant.now())) return false;
    var roles = strings(row.get("targetRoles"));
    if (!roles.isEmpty() && roles.stream().noneMatch(p::hasRole)) return false;
    var sites = ids(row.get("targetSites"));
    if (sites.isEmpty()) return true;
    for (long site : sites) {
      if (p.isStaff()
          && db.count(
                  "SELECT COUNT(*) FROM sys_user_site WHERE user_id=? AND site_id=?",
                  p.userId(),
                  site)
              > 0) return true;
      if (p.hasRole("FAMILY")
          && db.count(
                  "SELECT COUNT(*) FROM family_binding b JOIN elder e ON e.id=b.elder_id WHERE"
                      + " b.family_user_id=? AND b.status='APPROVED' AND e.site_id=?",
                  p.userId(),
                  site)
              > 0) return true;
    }
    return false;
  }

  public Map<String, Object> notices(Actor p, int page, int size, String keyword) {
    if (page < 1 || size < 1 || size > 100) throw ApiException.invalid("分页参数无效");
    var visible =
        db
            .list(
                "SELECT * FROM notice WHERE title LIKE ? ORDER BY created_at DESC,id DESC",
                "%" + keyword + "%")
            .stream()
            .filter(v -> noticeVisible(p, v))
            .toList();
    int start = Math.min(visible.size(), (page - 1) * size);
    return map(
        "records",
        visible.subList(start, Math.min(visible.size(), start + size)),
        "total",
        visible.size());
  }

  public Map<String, Object> notice(Actor p, long id) {
    var row = db.one("SELECT * FROM notice WHERE id=?", id);
    if (!noticeVisible(p, row)) throw ApiException.forbidden();
    row.put("receiveRoles", row.get("targetRoles"));
    row.put("siteIds", row.get("targetSites"));
    return row;
  }

  public Map<String, Object> saveNotice(Actor p, Long id, Map<String, Object> input) {
    access.permit(p, "notices:write", "ADMIN");
    String title = text(input, "title", 200, true),
        type = choice(input.get("type"), "CLOSURE", "ACTIVITY", "IMPORTANT", "GENERAL"),
        content = text(input, "content", 15000, true);
    var roles = strings(input.get("receiveRoles"));
    for (String role : roles) choice(role, "ADMIN", "CANTEEN", "FAMILY", "COMMUNITY");
    var sites = ids(input.get("siteIds"));
    for (long site : sites) db.one("SELECT id FROM service_site WHERE id=?", site);
    String expiry = text(input, "expireAt", 40, false);
    Object expires =
        expiry == null ? null : LocalDateTime.ofInstant(Instant.parse(expiry), ZoneOffset.UTC);
    if (id == null)
      id =
          db.insert(
              "INSERT INTO"
                  + " notice(title,type,content,target_roles_json,target_sites_json,expire_at,created_by)VALUES(?,?,?,?,?,?,?)",
              title,
              type,
              content,
              db.json(roles),
              db.json(sites),
              expires,
              p.userId());
    else {
      db.one("SELECT id FROM notice WHERE id=?", id);
      db.exec(
          "UPDATE notice SET"
              + " title=?,type=?,content=?,target_roles_json=?,target_sites_json=?,expire_at=?"
              + " WHERE id=?",
          title,
          type,
          content,
          db.json(roles),
          db.json(sites),
          expires,
          id);
    }
    db.audit(p, "NOTICE_SAVE", "NOTICE", id);
    var row = notice(p, id);
    row.put("receiveRoles", row.get("targetRoles"));
    row.put("siteIds", row.get("targetSites"));
    return row;
  }

  public Map<String, Object> publishNotice(Actor p, long id, boolean publish) {
    access.permit(p, "notices:write", "ADMIN");
    var before=db.one("SELECT * FROM notice WHERE id=? FOR UPDATE",id);
    if(str(before.get("status")).equals(publish?"PUBLISHED":"REVOKED"))return notice(p,id);
    db.exec(
        "UPDATE notice SET status=?,publish_at=IF(?,UTC_TIMESTAMP(),publish_at) WHERE id=?",
        publish ? "PUBLISHED" : "REVOKED",
        publish,
        id);
    db.audit(p, publish ? "NOTICE_PUBLISH" : "NOTICE_REVOKE", "NOTICE", id);
    return notice(p, id);
  }
}
