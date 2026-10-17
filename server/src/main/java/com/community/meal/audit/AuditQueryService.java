package com.community.meal.audit;

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
public class AuditQueryService {
  private final Db db;
  private final Access access;

  public AuditQueryService(Db db, Access access) {
    this.db = db;
    this.access = access;
  }

  private void admin(Actor p, String operation) {
    access.permit(p, operation, "ADMIN");
  }

  public Map<String, Object> logs(
      Actor p, int page, int size, String action, String dateFrom, String dateTo) {
    access.role(p, "ADMIN");
    List<Object> args = new ArrayList<>();
    String where = "1=1";
    if (!action.isBlank()) {
      where += " AND a.action=?";
      args.add(action);
    }
    if (!dateFrom.isBlank()) {
      where += " AND a.created_at>=?";
      args.add(date(dateFrom));
    }
    if (!dateTo.isBlank()) {
      where += " AND a.created_at<?";
      args.add(date(dateTo).plusDays(1));
    }
    return db.page(
        "SELECT a.*,u.username operator_name FROM audit_log a LEFT JOIN sys_user u ON"
            + " u.id=a.operator_user_id WHERE "
            + where
            + " ORDER BY a.id DESC",
        args,
        page,
        size);
  }
}
