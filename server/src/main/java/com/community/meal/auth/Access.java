package com.community.meal.auth;

import com.community.meal.common.*;
import com.community.meal.contracts.Actor;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class Access {
  private final Db db;

  public Access(Db db) {
    this.db = db;
  }

  public void role(Actor p, String... roles) {
    if (Arrays.stream(roles).noneMatch(p::hasRole)) throw ApiException.forbidden();
  }

  public void permit(Actor p, String operation, String... roles) {
    role(p, roles);
    if (db.count(
            "SELECT COUNT(*) FROM sys_user_permission WHERE user_id=? AND permission_code=?",
            p.userId(),
            operation)
        > 0) throw ApiException.forbidden();
  }

  public boolean assignedSite(Actor p, long site) {
    return db.count(
            "SELECT COUNT(*) FROM sys_user_site WHERE user_id=? AND site_id=?", p.userId(), site)
        > 0;
  }

  public boolean linkedFamily(Actor p, long elder) {
    return p.hasRole("FAMILY")
        && db.count(
                "SELECT COUNT(*) FROM family_binding WHERE family_user_id=? AND elder_id=? AND"
                    + " status='APPROVED'",
                p.userId(),
                elder)
            > 0;
  }

  public void site(Actor p, long site) {
    role(p, "ADMIN", "CANTEEN", "COMMUNITY");
    if (!p.hasRole("ADMIN")
        && db.count(
                "SELECT COUNT(*) FROM sys_user_site WHERE user_id=? AND site_id=?",
                p.userId(),
                site)
            == 0) throw ApiException.forbidden();
  }

  public void elder(Actor p, Map<String, Object> elder, boolean health) {
    if (p.hasRole("COMMUNITY")
        && db.count(
                "SELECT COUNT(*) FROM sys_user_site WHERE user_id=? AND site_id=?",
                p.userId(),
                elder.get("siteId"))
            > 0) return;
    if (p.hasRole("FAMILY")
        && db.count(
                "SELECT COUNT(*) FROM family_binding WHERE family_user_id=? AND elder_id=? AND"
                    + " status='APPROVED'",
                p.userId(),
                elder.get("id"))
            > 0) return;
    throw ApiException.forbidden();
  }

  public String siteCondition(Actor p, String column, List<Object> args) {
    if (p.hasRole("ADMIN")) return "1=1";
    args.add(p.userId());
    return column + " IN(SELECT site_id FROM sys_user_site WHERE user_id=?)";
  }
}
