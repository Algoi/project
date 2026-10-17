package com.community.meal;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.community.meal.dev.DemoSeed;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
@Transactional
class DemoAccountsIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder passwords;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired com.community.meal.common.Db db;
  final List<String> names = List.of("admin", "canteen", "family", "community");

  @Test
  void businessDemoHistoryIsConsistentAndIdempotent() {
    seed(false).run();
    new com.community.meal.dev.BusinessDemoSeed(db).run();
    long count = jdbc.queryForObject("SELECT COUNT(*) FROM meal_order", Long.class);
    assertThat(count).isGreaterThan(0);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM meal_order WHERE"
                    + " original_amount<>subsidy_amount+payable_amount",
                Long.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM redemption", Long.class)).isEqualTo(count);
    new com.community.meal.dev.BusinessDemoSeed(db).run();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM meal_order", Long.class)).isEqualTo(count);
  }

  @Test
  void freshSeedUsesPlainUsernamesAndMatchingPasswords() throws Exception {
    seed(false).run();
    assertThat(jdbc.queryForList("SELECT username FROM sys_user ORDER BY id", String.class))
        .containsExactlyElementsOf(names);
    for (String name : names) {
      String hash =
          jdbc.queryForObject(
              "SELECT password_hash FROM sys_user WHERE username=?", String.class, name);
      assertThat(hash).isNotEqualTo(name);
      assertThat(passwords.matches(name, hash)).isTrue();
      login(name, name);
      mvc.perform(
              post("/api/v1/auth/login")
                  .contentType("application/json")
                  .content(
                      json.writeValueAsString(
                          Map.of("username", name + "_demo", "password", name))))
          .andExpect(status().isUnauthorized());
    }
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM service_site", Integer.class))
        .isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_site", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void upgradingLegacyAccountsPreservesIdentityRolesAndBindingsAndRevokesSessions() {
    createLegacyAccounts();
    long familyId =
        jdbc.queryForObject("SELECT id FROM sys_user WHERE username='family_demo'", Long.class);
    List<Map<String, Object>> roles =
        jdbc.queryForList("SELECT * FROM sys_user_role ORDER BY user_id,role_id");
    List<Map<String, Object>> sites =
        jdbc.queryForList("SELECT * FROM sys_user_site ORDER BY user_id,site_id");
    jdbc.update("INSERT INTO elder(id,site_id,name) VALUES(1,1,'测试老人')");
    jdbc.update(
        "INSERT INTO family_binding(family_user_id,elder_id,status) VALUES(?,1,'APPROVED')",
        familyId);
    jdbc.update(
        "INSERT INTO auth_session(token_hash,user_id,expires_at)"
            + " VALUES(?,?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 DAY))",
        "a".repeat(64),
        familyId);
    seed(true).run();
    assertThat(jdbc.queryForObject("SELECT id FROM sys_user WHERE username='family'", Long.class))
        .isEqualTo(familyId);
    assertThat(
            jdbc.queryForObject(
                "SELECT family_user_id FROM family_binding WHERE elder_id=1", Long.class))
        .isEqualTo(familyId);
    assertThat(jdbc.queryForList("SELECT * FROM sys_user_role ORDER BY user_id,role_id"))
        .isEqualTo(roles);
    assertThat(jdbc.queryForList("SELECT * FROM sys_user_site ORDER BY user_id,site_id"))
        .isEqualTo(sites);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_session", Integer.class)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action='UPGRADE_DEMO_ACCOUNT'",
                Integer.class))
        .isEqualTo(4);
    for (String name : names) {
      assertThat(
              passwords.matches(
                  name,
                  jdbc.queryForObject(
                      "SELECT password_hash FROM sys_user WHERE username=?", String.class, name)))
          .isTrue();
    }
    String hash =
        jdbc.queryForObject(
            "SELECT password_hash FROM sys_user WHERE username='family'", String.class);
    seed(true).run();
    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM sys_user WHERE username='family'", String.class))
        .isEqualTo(hash);
  }

  @Test
  void usernameCollisionStopsUpgradeBeforeAnyDataChanges() {
    createLegacyAccounts();
    jdbc.update(
        "INSERT INTO sys_user(username,password_hash) VALUES('community',?)",
        passwords.encode("Unrelated-Password-2026!"));
    List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM sys_user ORDER BY id");
    assertThatThrownBy(() -> seed(true).run()).isInstanceOf(IllegalStateException.class);
    assertThat(jdbc.queryForList("SELECT * FROM sys_user ORDER BY id")).isEqualTo(before);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class)).isZero();
  }

  @Test
  void localDemoPasswordCanBeRestoredAfterPasswordChange() throws Exception {
    seed(false).run();
    String token = login("family", "family");
    changePassword(token, "family", "Changed-Password-2026!").andExpect(status().isOk());
    token = login("family", "Changed-Password-2026!");
    changePassword(token, "Changed-Password-2026!", "other").andExpect(status().isBadRequest());
    changePassword(token, "Changed-Password-2026!", "family").andExpect(status().isOk());
    login("family", "family");
  }

  @Test
  void ordinaryUsersStillCannotSetShortPasswordsInDev() throws Exception {
    jdbc.update(
        "INSERT INTO sys_user(id,username,password_hash) VALUES(100,'ordinary',?)",
        passwords.encode("Initial-Password-2026!"));
    jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(100,3)");
    changePassword(
            login("ordinary", "Initial-Password-2026!"), "Initial-Password-2026!", "ordinary")
        .andExpect(status().isBadRequest());
  }

  @Test
  void freshSeedRefusesToOverwriteExistingUsers() {
    jdbc.update(
        "INSERT INTO sys_user(username,password_hash) VALUES('existing',?)",
        passwords.encode("Existing-Password-2026!"));
    assertThatThrownBy(() -> seed(false).run()).isInstanceOf(IllegalStateException.class);
    assertThat(jdbc.queryForList("SELECT username FROM sys_user", String.class))
        .containsExactly("existing");
  }

  private DemoSeed seed(boolean upgrade) {
    return new DemoSeed(
        jdbc,
        passwords,
        new MockEnvironment()
            .withProperty("DEMO_PASSWORD", "Legacy-Password-2026!")
            .withProperty("app.seed.upgrade-legacy", Boolean.toString(upgrade)));
  }

  private void createLegacyAccounts() {
    jdbc.update("INSERT INTO service_site(id,name) VALUES(1,'原有网点')");
    for (int i = 0; i < names.size(); i++) {
      jdbc.update(
          "INSERT INTO sys_user(id,username,password_hash,real_name) VALUES(?,?,?,?)",
          i + 1,
          names.get(i) + "_demo",
          passwords.encode("Legacy-Password-2026!"),
          "保留姓名" + i);
      jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", i + 1, i + 1);
    }
    jdbc.update("INSERT INTO sys_user_site(user_id,site_id) VALUES(2,1),(4,1)");
  }

  private String login(String username, String password) throws Exception {
    String response =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of("username", username, "password", password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response).path("data").path("token").asText();
  }

  private org.springframework.test.web.servlet.ResultActions changePassword(
      String token, String oldPassword, String newPassword) throws Exception {
    return mvc.perform(
        put("/api/v1/auth/password")
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .content(
                json.writeValueAsString(
                    Map.of("oldPassword", oldPassword, "newPassword", newPassword))));
  }
}
