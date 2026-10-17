package com.community.meal;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class AdminBusinessTest extends BusinessTestSupport {
  @Test
  void usersCanBeCreatedAssignedDisabledAndResetWithoutLeakingCredentials() throws Exception {
    var created =
        call(
            "POST",
            "/users",
            Map.of(
                "username",
                "new_staff",
                "password",
                PASSWORD,
                "realName",
                "新员工",
                "roles",
                List.of("CANTEEN"),
                "siteIds",
                List.of(2)),
            "admin",
            200);
    long id = created.path("id").asLong();
    assertThat(id).isPositive();
    assertThat(created.has("passwordHash")).isFalse();
    var login =
        call(
            "POST",
            "/auth/login",
            Map.of("username", "new_staff", "password", PASSWORD),
            null,
            200);
    tokens.put("new_staff", login.path("token").asText());
    call("GET", "/sites/1", null, "new_staff", 403);
    call("GET", "/sites/2", null, "new_staff", 200);
    call("PUT", "/users/" + id + "/roles", Map.of("roles", List.of("COMMUNITY")), "admin", 200);
    call("PUT", "/users/" + id + "/sites", Map.of("siteIds", List.of(1)), "admin", 200);
    call(
        "POST",
        "/users/" + id + "/reset-password",
        Map.of("password", "Reset-Password-2026!"),
        "admin",
        200);
    call("GET", "/auth/me", null, "new_staff", 401);
    call("POST", "/auth/login", Map.of("username", "new_staff", "password", PASSWORD), null, 401);
    call("PATCH", "/users/" + id + "/status", Map.of("status", "DISABLED"), "admin", 200);
    call(
        "POST",
        "/auth/login",
        Map.of("username", "new_staff", "password", "Reset-Password-2026!"),
        null,
        401);
  }

  @Test
  void administratorCannotDisableOrDemoteLastAdministrator() throws Exception {
    call("PATCH", "/users/11/status", Map.of("status", "DISABLED"), "admin", 422);
    call("PUT", "/users/11/roles", Map.of("roles", List.of("FAMILY")), "admin", 422);
    call("PUT", "/users/11/permissions", Map.of("denied", List.of("users:write")), "admin", 422);
    call(
        "PUT",
        "/users/11/permissions",
        Map.of("denied", List.of("menu:/admin/users")),
        "admin",
        422);
    assertThat(jdbc.queryForObject("SELECT status FROM sys_user WHERE id=11", String.class))
        .isEqualTo("ACTIVE");
  }

  @Test
  void wrongRolesAndAnonymousCannotManageData() throws Exception {
    for (String role : List.of("family", "community", "canteen")) {
      call("GET", "/users", null, role, 403);
      call("POST", "/sites", Map.of("name", "无权网点"), role, 403);
      call("POST", "/dishes", Map.of(), role, 403);
      call("GET", "/audit-logs", null, role, 403);
    }
    call("GET", "/users", null, null, 401);
  }

  @Test
  void sitesCalendarAndBusinessStatusArePersisted() throws Exception {
    long id =
        call(
                "POST",
                "/sites",
                Map.of(
                    "name",
                    "新增网点",
                    "address",
                    "测试路",
                    "phone",
                    "02112345678",
                    "serviceArea",
                    "测试社区"),
                "admin",
                200)
            .path("id")
            .asLong();
    call(
        "PUT",
        "/sites/" + id,
        Map.of("name", "修改网点", "address", "新地址", "phone", "02112345678", "serviceArea", "新服务区"),
        "admin",
        200);
    call(
        "PUT",
        "/sites/" + id + "/closed-dates",
        Map.of("dates", List.of(Map.of("date", "2026-11-01", "mealType", "LUNCH", "reason", "维护"))),
        "admin",
        200);
    assertThat(call("GET", "/sites/" + id + "/closed-dates", null, "admin", 200).size())
        .isEqualTo(1);
    call("PATCH", "/sites/" + id + "/status", Map.of("status", "CLOSED"), "admin", 200);
    assertThat(call("GET", "/sites/" + id, null, "admin", 200).path("businessStatus").asText())
        .isEqualTo("CLOSED");
  }

  @Test
  void dishPricesNutritionAndAllergensPersistAndAreValidated() throws Exception {
    long category =
        call("POST", "/dish-categories", Map.of("name", "荤菜"), "admin", 200).path("id").asLong();
    long allergen =
        call("POST", "/allergens", Map.of("name", "花生"), "admin", 200).path("id").asLong();
    Map<String, Object> input =
        new HashMap<>(
            Map.of(
                "name",
                "测试菜品",
                "categoryId",
                category,
                "originalPrice",
                "12.50",
                "elderPrice",
                "8.50",
                "ingredients",
                List.of("花生", "鸡肉"),
                "allergenIds",
                List.of(allergen),
                "allergenDataComplete",
                true,
                "weightGrams",
                "200.00",
                "caloriesKcal",
                "300.00",
                "proteinGrams",
                "20.00"));
    long dish = call("POST", "/dishes", input, "admin", 200).path("id").asLong();
    var saved = call("GET", "/dishes/" + dish, null, "canteen", 200);
    assertThat(saved.path("originalPrice").asText()).isEqualTo("12.50");
    assertThat(saved.path("allergenIds").get(0).asLong()).isEqualTo(allergen);
    input.put("originalPrice", "-1.00");
    call("PUT", "/dishes/" + dish, input, "admin", 400);
    input.put("originalPrice", "12.50");
    input.put("elderPrice", "13.00");
    call("PUT", "/dishes/" + dish, input, "admin", 400);
    call("PATCH", "/dishes/" + dish + "/status", Map.of("status", "DISABLED"), "admin", 200);
    assertThat(jdbc.queryForObject("SELECT status FROM dish WHERE id=?", String.class, dish))
        .isEqualTo("DISABLED");
  }

  @Test
  void granularOperationDenialIsEnforcedByBackend() throws Exception {
    call(
        "PUT",
        "/users/11/permissions",
        Map.of("denied", List.of("dishes:write", "menu:/admin/dishes")),
        "admin",
        200);
    assertThat(call("GET", "/auth/me", null, "admin", 200).path("deniedPermissions").toString())
        .contains("menu:/admin/dishes");
    call("PUT", "/users/12/permissions", Map.of("denied", List.of("menu:/unknown")), "admin", 400);
    call("POST", "/dishes", Map.of("name", "不能创建"), "admin", 403);
    call("GET", "/dishes", null, "admin", 200);
  }

  @Test
  void auditSupportsFilteringWithoutPlaintextSecrets() throws Exception {
    call("POST", "/sites", Map.of("name", "审计网点"), "admin", 200);
    var logs = call("GET", "/audit-logs?action=SITE_CREATE", null, "admin", 200);
    assertThat(logs.path("total").asLong()).isEqualTo(1);
    assertThat(logs.toString()).doesNotContain(PASSWORD, "passwordHash");
  }
}
