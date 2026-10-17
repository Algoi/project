package com.community.meal.dev;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev & !prod")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DemoSeed implements CommandLineRunner {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DemoSeed.class);
  private static final List<String> USERNAMES = List.of("admin", "canteen", "family", "community");
  private static final List<String> ROLES = List.of("ADMIN", "CANTEEN", "FAMILY", "COMMUNITY");
  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwords;
  private final org.springframework.core.env.Environment environment;

  public DemoSeed(
      JdbcTemplate jdbc,
      PasswordEncoder passwords,
      org.springframework.core.env.Environment environment) {
    this.jdbc = jdbc;
    this.passwords = passwords;
    this.environment = environment;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (environment.getProperty("app.seed.upgrade-legacy", Boolean.class, false)) {
      upgradeLegacyAccounts();
    } else {
      initialize();
    }
  }

  private void initialize() {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Long.class) != 0) {
      throw new IllegalStateException(
          "Database already contains users; refusing to overwrite. Restart without"
              + " app.seed.enabled.");
    }
    String[] names = {"系统管理员", "食堂工作人员", "家属用户", "社区操作员"};
    for (int i = 0; i < USERNAMES.size(); i++) {
      String username = USERNAMES.get(i);
      jdbc.update(
          "INSERT INTO sys_user(username,password_hash,real_name) VALUES(?,?,?)",
          username,
          passwords.encode(username),
          names[i]);
      jdbc.update(
          "INSERT INTO sys_user_role(user_id,role_id) SELECT u.id,r.id FROM sys_user u JOIN"
              + " sys_role r ON r.code=? WHERE u.username=?",
          ROLES.get(i),
          username);
    }
    jdbc.update(
        "INSERT INTO service_site(name,address,phone,service_area)"
            + " VALUES('幸福社区助餐点','幸福社区服务中心一楼','021-55550101','幸福社区'),('康乐社区助餐点','康乐社区活动中心','021-55550102','康乐社区')");
    jdbc.update(
        "INSERT INTO sys_user_site(user_id,site_id) SELECT u.id,s.id FROM sys_user u JOIN"
            + " service_site s ON s.name='幸福社区助餐点' WHERE u.username IN('canteen','community')");
    log.info("Initialized four local demo accounts and two service sites");
  }

  private record Upgrade(long userId, String username) {}

  private void upgradeLegacyAccounts() {
    List<Upgrade> upgrades = new ArrayList<>();
    // Preflight every account before changing any row; collisions never overwrite existing users.
    for (int i = 0; i < USERNAMES.size(); i++) {
      String username = USERNAMES.get(i);
      List<Long> legacy =
          jdbc.queryForList(
              "SELECT id FROM sys_user WHERE username=? FOR UPDATE",
              Long.class,
              username + "_demo");
      if (legacy.isEmpty()) continue;
      List<Long> target =
          jdbc.queryForList(
              "SELECT id FROM sys_user WHERE username=? FOR UPDATE", Long.class, username);
      if (!target.isEmpty())
        throw new IllegalStateException(
            "Username already exists: " + username + "; refusing to overwrite");
      long userId = legacy.get(0);
      int roleCount =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE"
                  + " ur.user_id=? AND r.code=?",
              Integer.class,
              userId,
              ROLES.get(i));
      if (roleCount == 0)
        throw new IllegalStateException("Legacy demo account has unexpected roles: " + username);
      upgrades.add(new Upgrade(userId, username));
    }
    for (Upgrade upgrade : upgrades) {
      jdbc.update(
          "UPDATE sys_user SET username=?,password_hash=? WHERE id=?",
          upgrade.username(),
          passwords.encode(upgrade.username()),
          upgrade.userId());
      jdbc.update("DELETE FROM auth_session WHERE user_id=?", upgrade.userId());
      jdbc.update(
          "INSERT INTO audit_log(operator_user_id,action,resource_type,resource_id,success,summary)"
              + " VALUES(?,'UPGRADE_DEMO_ACCOUNT','USER',?,1,'本地演示账号更名并更新凭据，撤销旧会话')",
          upgrade.userId(),
          Long.toString(upgrade.userId()));
    }
    log.info("Upgraded {} local demo accounts", upgrades.size());
  }
}
