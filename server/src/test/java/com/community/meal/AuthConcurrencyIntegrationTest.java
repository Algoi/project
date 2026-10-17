package com.community.meal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthConcurrencyIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  long userId;
  long initialAuditId;
  String username;
  final String password = "Initial-Password-2026!";

  @BeforeEach
  void seedCommittedUser() {
    initialAuditId = jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM audit_log", Long.class);
    username = "race_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.update(
        "INSERT INTO sys_user(username,password_hash) VALUES(?,?)",
        username,
        new BCryptPasswordEncoder(4).encode(password));
    userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
    jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,3)", userId);
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM auth_session WHERE user_id=?", userId);
    jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", userId);
    jdbc.update("DELETE FROM audit_log WHERE id>?", initialAuditId);
    jdbc.update("DELETE FROM sys_user WHERE id=?", userId);
  }

  @Test
  void concurrentPasswordChangesOnlyAllowOneSuccessAndRevokeOldToken() throws Exception {
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
    String token = json.readTree(response).path("data").path("token").asText();
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<Integer> a = pool.submit(() -> change(start, token, "Concurrent-Password-A!"));
      Future<Integer> b = pool.submit(() -> change(start, token, "Concurrent-Password-B!"));
      start.countDown();
      assertThat(java.util.List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 401);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM auth_session WHERE user_id=?", Integer.class, userId))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM audit_log WHERE action='CHANGE_PASSWORD' AND"
                      + " operator_user_id=?",
                  Integer.class,
                  userId))
          .isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void failedLoginAuditIsCommittedDespiteUnauthorizedResponse() throws Exception {
    int before =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE action='LOGIN' AND success=0", Integer.class);
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(Map.of("username", username, "password", "incorrect"))))
        .andExpect(status().isUnauthorized());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action='LOGIN' AND success=0", Integer.class))
        .isEqualTo(before + 1);
  }

  private int change(CountDownLatch start, String token, String nextPassword) throws Exception {
    start.await(5, TimeUnit.SECONDS);
    return mvc.perform(
            put("/api/v1/auth/password")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("oldPassword", password, "newPassword", nextPassword))))
        .andReturn()
        .getResponse()
        .getStatus();
  }
}
