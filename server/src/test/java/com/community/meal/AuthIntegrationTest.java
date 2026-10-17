package com.community.meal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  private static final String PASSWORD = "Test-Password-2026!";

  @BeforeEach
  void seed() {
    String hash = new BCryptPasswordEncoder(4).encode(PASSWORD);
    for (int i = 1; i <= 4; i++) {
      String username = new String[] {"admin", "canteen", "family", "community"}[i - 1];
      jdbc.update(
          "INSERT INTO sys_user(id,username,password_hash,real_name,phone) VALUES (?,?,?,?,?)",
          i,
          username,
          hash,
          username,
          "13812345678");
      jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,?)", i, i);
    }
    jdbc.update("INSERT INTO service_site(id,name) VALUES (1,'第一网点'),(2,'第二网点')");
    jdbc.update("INSERT INTO sys_user_site(user_id,site_id) VALUES (2,1),(4,1)");
  }

  @ParameterizedTest
  @ValueSource(strings = {"admin", "canteen", "family", "community"})
  void fourRolesCanLoginAndReadOwnProfile(String username) throws Exception {
    String token = login(username);
    mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.username").value(username))
        .andExpect(jsonPath("$.data.roles[0]").value(username.toUpperCase()))
        .andExpect(jsonPath("$.data.phoneMasked").value("138****5678"))
        .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.data.phone").doesNotExist())
        .andExpect(jsonPath("$.traceId").isNotEmpty());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM auth_session WHERE token_hash=?", Integer.class, token))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT CHAR_LENGTH(token_hash) FROM auth_session LIMIT 1", Integer.class))
        .isEqualTo(64);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/auth/me", "/roles", "/sites", "/sites/1", "/openapi.yaml"})
  void anonymousRequestsReturnJson401(String path) throws Exception {
    mvc.perform(get("/api/v1" + path))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  @Test
  void loginFailureDoesNotRevealAccountExistenceAndIsAudited() throws Exception {
    String a =
        postJson("/auth/login", Map.of("username", "admin", "password", "wrong"), null)
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String b =
        postJson("/auth/login", Map.of("username", "missing", "password", "wrong"), null)
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(json.readTree(a).get("message")).isEqualTo(json.readTree(b).get("message"));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action='LOGIN' AND success=0", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void invalidLoginFieldsReturn400() throws Exception {
    postJson("/auth/login", Map.of("username", "", "password", ""), null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"profile", "password", "logout"})
  void anonymousRequestsCannotMutateAccounts(String action) throws Exception {
    var request =
        action.equals("logout") ? post("/api/v1/auth/" + action) : put("/api/v1/auth/" + action);
    mvc.perform(request.contentType("application/json").content("{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void apiContractCanBeDownloadedAfterLogin() throws Exception {
    mvc.perform(get("/api/v1/openapi.yaml").header("Authorization", bearer(login("admin"))))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("openapi: 3.0.3")));
  }

  @Test
  void utf8PasswordsCannotExceedBcryptByteLimitOrReuseOldPassword() throws Exception {
    String token = login("family");
    changePassword(token, PASSWORD, "长".repeat(25)).andExpect(status().isBadRequest());
    changePassword(token, PASSWORD, PASSWORD).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
        .andExpect(status().isOk());
  }

  @Test
  void administratorCanReadRolesAndAllSites() throws Exception {
    String token = login("admin");
    mvc.perform(get("/api/v1/roles").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(4));
    mvc.perform(get("/api/v1/sites").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(2));
    mvc.perform(get("/api/v1/sites/2").header("Authorization", bearer(token)))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(strings = {"canteen", "community"})
  void staffCanOnlyReadAssignedSites(String username) throws Exception {
    String token = login(username);
    mvc.perform(get("/api/v1/sites").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.records[0].id").value(1));
    mvc.perform(get("/api/v1/sites/1").header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/sites/2").header("Authorization", bearer(token)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mvc.perform(get("/api/v1/roles").header("Authorization", bearer(token)))
        .andExpect(status().isForbidden());
  }

  @Test
  void familyCannotReadStaffSitesEvenWithAnAccidentalSiteAssignment() throws Exception {
    jdbc.update("INSERT INTO sys_user_site(user_id,site_id) VALUES(3,1)");
    String token = login("family");
    mvc.perform(get("/api/v1/sites/1").header("Authorization", bearer(token)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/sites").header("Authorization", bearer(token)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sites.length()").value(0));
  }

  @Test
  void logoutRevokesOnlyTheCurrentSession() throws Exception {
    String current = login("family"), other = login("family");
    postJson("/auth/logout", Map.of(), current).andExpect(status().isOk());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(current)))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(other)))
        .andExpect(status().isOk());
  }

  @Test
  void expiredAndMalformedTokensAreRejected() throws Exception {
    String token = login("family");
    jdbc.update("UPDATE auth_session SET expires_at='2000-01-01 00:00:00'");
    for (String candidate : new String[] {token, "invalid", "x".repeat(2000)}) {
      mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(candidate)))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  void disabledUsersCannotLoginAndExistingSessionsStopWorking() throws Exception {
    String token = login("canteen");
    jdbc.update("UPDATE sys_user SET status='DISABLED' WHERE id=2");
    mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
        .andExpect(status().isUnauthorized());
    postJson("/auth/login", Map.of("username", "canteen", "password", PASSWORD), null)
        .andExpect(status().isUnauthorized());
  }

  @Test
  void roleAndSiteRevocationTakesEffectOnNextRequest() throws Exception {
    String token = login("canteen");
    jdbc.update("DELETE FROM sys_user_site WHERE user_id=2");
    mvc.perform(get("/api/v1/sites/1").header("Authorization", bearer(token)))
        .andExpect(status().isForbidden());
    jdbc.update("DELETE FROM sys_user_role WHERE user_id=2");
    mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void profileUpdateIsLimitedToCurrentUsersEditableFields() throws Exception {
    String token = login("family");
    mvc.perform(
            put("/api/v1/auth/profile")
                .header("Authorization", bearer(token))
                .contentType("application/json")
                .content(
                    json.writeValueAsString(Map.of("realName", "张女士", "phone", "13912345678"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.realName").value("张女士"));
    assertThat(jdbc.queryForObject("SELECT real_name FROM sys_user WHERE id=3", String.class))
        .isEqualTo("张女士");
    mvc.perform(
            put("/api/v1/auth/profile")
                .header("Authorization", bearer(token))
                .contentType("application/json")
                .content("{\"id\":1,\"roles\":[\"ADMIN\"],\"realName\":\"x\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void passwordChangeRequiresOldPasswordAndRevokesAllSessions() throws Exception {
    String token = login("family"), other = login("family");
    changePassword(token, "wrong", "New-Password-2026!")
        .andExpect(status().isUnprocessableEntity());
    changePassword(token, PASSWORD, "short").andExpect(status().isBadRequest());
    changePassword(token, PASSWORD, "New-Password-2026!").andExpect(status().isOk());
    for (String candidate : new String[] {token, other}) {
      mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(candidate)))
          .andExpect(status().isUnauthorized());
    }
    postJson("/auth/login", Map.of("username", "family", "password", PASSWORD), null)
        .andExpect(status().isUnauthorized());
    postJson("/auth/login", Map.of("username", "family", "password", "New-Password-2026!"), null)
        .andExpect(status().isOk());
    String hash =
        jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id=3", String.class);
    assertThat(hash).startsWith("$2").doesNotContain("New-Password");
    assertThat(jdbc.queryForList("SELECT summary FROM audit_log", String.class))
        .allSatisfy(
            summary ->
                assertThat(summary).doesNotContain(PASSWORD, "New-Password", token, "13812345678"));
  }

  @Test
  void multiRoleAccountReceivesAllAssignedRoles() throws Exception {
    jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(2,4)");
    mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(login("canteen"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.roles.length()").value(2));
  }

  @Test
  void sitePaginationAndUnknownResourcesHaveStableErrors() throws Exception {
    String token = login("admin");
    mvc.perform(get("/api/v1/sites?page=1&size=1").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.records.length()").value(1))
        .andExpect(jsonPath("$.data.total").value(2));
    mvc.perform(get("/api/v1/sites?page=0").header("Authorization", bearer(token)))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/sites/999").header("Authorization", bearer(token)))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/not-implemented").header("Authorization", bearer(token)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void incorrectHttpMethodReturns405RatherThanInternalError() throws Exception {
    mvc.perform(post("/api/v1/auth/me").header("Authorization", bearer(login("admin"))))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  private ResultActions changePassword(String token, String oldPassword, String newPassword)
      throws Exception {
    return mvc.perform(
        put("/api/v1/auth/password")
            .header("Authorization", bearer(token))
            .contentType("application/json")
            .content(
                json.writeValueAsString(
                    Map.of("oldPassword", oldPassword, "newPassword", newPassword))));
  }

  private String login(String username) throws Exception {
    JsonNode response =
        json.readTree(
            postJson("/auth/login", Map.of("username", username, "password", PASSWORD), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return response.path("data").path("token").asText();
  }

  private ResultActions postJson(String path, Object body, String token) throws Exception {
    var request =
        post("/api/v1" + path)
            .contentType("application/json")
            .content(json.writeValueAsString(body));
    if (token != null) request.header("Authorization", bearer(token));
    return mvc.perform(request);
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
