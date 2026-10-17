package com.community.meal;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
abstract class BusinessTestSupport {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired PasswordEncoder encoder;
  protected final Map<String, String> tokens = new HashMap<>();
  protected static final String PASSWORD = "Testing-Password-2026!";

  @BeforeEach
  void setupBusinessUsers() throws Exception {
    String[] names = {"admin", "canteen", "family", "community"};
    for (int i = 0; i < 4; i++) {
      jdbc.update(
          "INSERT INTO sys_user(id,username,password_hash,real_name) VALUES(?,?,?,?)",
          11 + i,
          names[i],
          encoder.encode(PASSWORD),
          names[i]);
      jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", 11 + i, 1 + i);
    }
    jdbc.update("INSERT INTO service_site(id,name) VALUES(1,'测试第一网点'),(2,'测试第二网点')");
    jdbc.update("INSERT INTO sys_user_site VALUES(12,1),(14,1)");
    for (String name : names)
      tokens.put(
          name,
          call("POST", "/auth/login", Map.of("username", name, "password", PASSWORD), null, 200)
              .path("token")
              .asText());
  }

  protected JsonNode call(String method, String path, Object body, String role, int status)
      throws Exception {
    response(method, path, body, role).andExpect(status().is(status));
    return last;
  }

  private JsonNode last;

  protected ResultActions response(String method, String path, Object body, String role)
      throws Exception {
    var request =
        MockMvcRequestBuilders.request(
            org.springframework.http.HttpMethod.valueOf(method), "/api/v1" + path);
    if (role != null) request.header("Authorization", "Bearer " + tokens.get(role));
    if (body != null)
      request.contentType("application/json").content(json.writeValueAsString(body));
    var result = mvc.perform(request);
    var content = result.andReturn().getResponse().getContentAsString();
    try {
      last = json.readTree(content).path("data");
    } catch (Exception ignored) {
      last = json.nullNode();
    }
    return result;
  }
}
