package com.community.meal.auth;

import static com.community.meal.common.Values.*;

import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class UserAdminService {
  private final Db db;
  private final Access access;
  private final org.springframework.security.crypto.password.PasswordEncoder passwords;
  private final NewPasswordPolicy policy;

  public UserAdminService(
      Db db,
      Access access,
      org.springframework.security.crypto.password.PasswordEncoder passwords,
      NewPasswordPolicy policy) {
    this.db = db;
    this.access = access;
    this.passwords = passwords;
    this.policy = policy;
  }

  private void admin(Actor p, String operation) {
    access.permit(p, operation, "ADMIN");
  }

  public static final List<String> OPERATIONS =
      List.of(
          "users:write",
          "sites:write",
          "dishes:write",
          "elders:write",
          "health:write",
          "subsidies:review",
          "bindings:review",
          "menus:write",
          "menus:publish",
          "orders:create",
          "orders:cancel",
          "orders:prepare",
          "redemptions:write",
          "delivery:write",
          "refunds:review",
          "preparation:write",
          "recommendation:write",
          "reviews:write",
          "tickets:write",
          "notices:write");
  public static final Set<String> MENUS =
      Set.of(
          "menu:/admin/users",
          "menu:/admin/sites",
          "menu:/admin/dishes",
          "menu:/admin/audit",
          "menu:/admin/recommendation-settings",
          "menu:/admin/notices",
          "menu:/admin/dashboard",
          "menu:/admin/tickets",
          "menu:/canteen/menus",
          "menu:/canteen/forecasts",
          "menu:/canteen/preparation",
          "menu:/canteen/orders",
          "menu:/canteen/redemptions",
          "menu:/canteen/delivery",
          "menu:/canteen/reviews",
          "menu:/canteen/notices",
          "menu:/family/elders",
          "menu:/family/order/new",
          "menu:/family/orders",
          "menu:/family/payments",
          "menu:/family/reviews",
          "menu:/family/tickets",
          "menu:/family/notices",
          "menu:/family/nutrition",
          "menu:/community/elders",
          "menu:/community/subsidies",
          "menu:/community/menus",
          "menu:/community/proxy-orders",
          "menu:/community/orders",
          "menu:/community/refunds",
          "menu:/community/redemptions",
          "menu:/community/delivery",
          "menu:/community/tickets",
          "menu:/community/notices",
          "menu:/community/nutrition");

  public Map<String, Object> users(Actor p, int page, int size, String keyword) {
    access.role(p, "ADMIN");
    return db.page(
        "SELECT id,username,real_name,status,created_at FROM sys_user WHERE username LIKE ? OR"
            + " real_name LIKE ? ORDER BY id",
        List.of("%" + keyword + "%", "%" + keyword + "%"),
        page,
        size);
  }

  public Map<String, Object> user(Actor p, long id) {
    access.role(p, "ADMIN");
    var row = db.one("SELECT id,username,real_name,status,created_at FROM sys_user WHERE id=?", id);
    row.put(
        "roles",
        db
            .list(
                "SELECT r.code FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id WHERE"
                    + " ur.user_id=? ORDER BY r.id",
                id)
            .stream()
            .map(r -> r.get("code"))
            .toList());
    row.put(
        "siteIds",
        db.list("SELECT site_id FROM sys_user_site WHERE user_id=? ORDER BY site_id", id).stream()
            .map(r -> r.get("siteId"))
            .toList());
    row.put(
        "denied",
        db.list("SELECT permission_code FROM sys_user_permission WHERE user_id=?", id).stream()
            .map(r -> r.get("permissionCode"))
            .toList());
    return row;
  }

  public Map<String, Object> saveUser(Actor p, Long id, Map<String, Object> input) {
    admin(p, "users:write");
    fields(input, "username", "password", "realName", "phone", "roles", "siteIds");
    String name = text(input, "realName", 80, true), phone = text(input, "phone", 30, false);
    if (id == null) {
      String username = text(input, "username", 64, true);
      if (!username.matches("[A-Za-z0-9_]{3,64}")) throw ApiException.invalid("账号格式无效");
      String password = text(input, "password", 72, true);
      policy.validate(username, password);
      if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
        throw ApiException.invalid("密码过长");
      id =
          db.insert(
              "INSERT INTO sys_user(username,password_hash,real_name,phone) VALUES(?,?,?,?)",
              username,
              passwords.encode(password),
              name,
              phone);
      assignRoles(p, id, map("roles", input.getOrDefault("roles", List.of("FAMILY"))));
      assignSites(p, id, map("siteIds", input.getOrDefault("siteIds", List.of())));
    } else {
      user(p, id);
      db.exec(
          "UPDATE sys_user SET real_name=?,phone=COALESCE(?,phone) WHERE id=?", name, phone, id);
    }
    db.audit(p, "USER_SAVE", "USER", id);
    return user(p, id);
  }

  private void lockAdministrators() {
    db.one("SELECT * FROM sys_role WHERE code='ADMIN' FOR UPDATE");
  }

  private void protectAdministrator(long id, boolean removing) {
    if (removing
        && db.count(
                "SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id JOIN"
                    + " sys_user u ON u.id=ur.user_id WHERE ur.user_id=? AND r.code='ADMIN' AND"
                    + " u.status='ACTIVE' AND NOT EXISTS(SELECT 1 FROM sys_user_permission q WHERE"
                    + " q.user_id=u.id AND q.permission_code"
                    + " IN('users:write','menu:/admin/users'))",
                id)
            > 0)
      rule(
          db.count(
                  "SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id JOIN"
                      + " sys_user u ON u.id=ur.user_id WHERE r.code='ADMIN' AND u.status='ACTIVE'"
                      + " AND NOT EXISTS(SELECT 1 FROM sys_user_permission q WHERE q.user_id=u.id"
                      + " AND q.permission_code IN('users:write','menu:/admin/users'))")
              > 1,
          "至少保留一名启用且能够管理用户的管理员");
  }

  public Map<String, Object> statusUser(Actor p, long id, Map<String, Object> input) {
    admin(p, "users:write");
    lockAdministrators();
    user(p, id);
    String status = choice(input.get("status"), "ACTIVE", "DISABLED");
    protectAdministrator(id, status.equals("DISABLED"));
    db.exec("UPDATE sys_user SET status=? WHERE id=?", status, id);
    if (status.equals("DISABLED")) db.exec("DELETE FROM auth_session WHERE user_id=?", id);
    db.audit(p, "USER_STATUS", "USER", id);
    return user(p, id);
  }

  public Map<String, Object> assignRoles(Actor p, long id, Map<String, Object> input) {
    admin(p, "users:write");
    lockAdministrators();
    user(p, id);
    var roles = strings(input.get("roles"));
    rule(!roles.isEmpty(), "至少选择一个角色");
    for (String code : roles)
      if (db.count("SELECT COUNT(*) FROM sys_role WHERE code=?", code) == 0)
        throw ApiException.invalid("角色不存在");
    protectAdministrator(id, !roles.contains("ADMIN"));
    db.exec("DELETE FROM sys_user_role WHERE user_id=?", id);
    for (String code : roles)
      db.exec(
          "INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE code=?",
          id,
          code);
    db.audit(p, "USER_ROLES", "USER", id);
    return user(p, id);
  }

  public Map<String, Object> assignSites(Actor p, long id, Map<String, Object> input) {
    admin(p, "users:write");
    user(p, id);
    var sites = ids(input.get("siteIds"));
    for (long site : sites) db.one("SELECT id FROM service_site WHERE id=?", site);
    db.exec("DELETE FROM sys_user_site WHERE user_id=?", id);
    for (long site : sites)
      db.exec("INSERT INTO sys_user_site(user_id,site_id) VALUES(?,?)", id, site);
    db.audit(p, "USER_SITES", "USER", id);
    return user(p, id);
  }

  public void resetPassword(Actor p, long id, Map<String, Object> input) {
    admin(p, "users:write");
    var user = user(p, id);
    String password = text(input, "password", 72, true);
    policy.validate(str(user.get("username")), password);
    if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw ApiException.invalid("密码过长");
    db.exec("UPDATE sys_user SET password_hash=? WHERE id=?", passwords.encode(password), id);
    db.exec("DELETE FROM auth_session WHERE user_id=?", id);
    db.audit(p, "USER_PASSWORD_RESET", "USER", id);
  }

  public Map<String, Object> permissions(Actor p, long id, Map<String, Object> input) {
    admin(p, "users:write");
    lockAdministrators();
    user(p, id);
    var denied = strings(input.get("denied"));
    if (!denied.stream().allMatch(code -> OPERATIONS.contains(code) || MENUS.contains(code)))
      throw ApiException.invalid("未知操作权限");
    protectAdministrator(
        id, denied.contains("users:write") || denied.contains("menu:/admin/users"));
    db.exec("DELETE FROM sys_user_permission WHERE user_id=?", id);
    for (String operation : denied)
      db.exec("INSERT INTO sys_user_permission VALUES(?,?)", id, operation);
    db.audit(p, "USER_PERMISSIONS", "USER", id);
    return user(p, id);
  }
}
