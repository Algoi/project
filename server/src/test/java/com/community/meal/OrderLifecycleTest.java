package com.community.meal;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class OrderLifecycleTest extends BusinessTestSupport {
  @org.springframework.beans.factory.annotation.Autowired
  org.springframework.context.ApplicationContext context;

  @Test
  void deliveryUsesItsOwnCompletionPathAndNotSiteRedemption() throws Exception {
    meals();
    Map<String, Object> input = new HashMap<>(order("DELIVERY"));
    call("POST", "/orders", input, "family", 400);
    input.put("deliveryAddress", "测试配送地址");
    input.put("contactName", "测试家属");
    input.put("contactPhone", "13800001111");
    long id = call("POST", "/orders", input, "family", 200).path("id").asLong();
    long pay =
        call("POST", "/orders/" + id + "/payments", Map.of("channel", "MOCK_B"), "family", 200)
            .path("id")
            .asLong();
    call("POST", "/dev/payments/" + pay + "/simulate-success", Map.of(), "family", 200);
    call("POST", "/orders/" + id + "/start-preparing", Map.of(), "canteen", 200);
    call("POST", "/orders/" + id + "/ready", Map.of(), "canteen", 200);
    call("GET", "/orders/" + id + "/pickup-voucher", null, "family", 422);
    assertThat(
            call(
                    "GET",
                    "/sites/1/delivery-list?date=" + date + "&mealType=LUNCH",
                    null,
                    "canteen",
                    200)
                .size())
        .isEqualTo(1);
    call(
        "PATCH",
        "/orders/" + id + "/delivery-status",
        Map.of("status", "OUT_FOR_DELIVERY"),
        "canteen",
        200);
    call(
        "PATCH",
        "/orders/" + id + "/delivery-status",
        Map.of("status", "DELIVERED"),
        "community",
        200);
    assertThat(jdbc.queryForObject("SELECT status FROM meal_order WHERE id=?", String.class, id))
        .isEqualTo("COMPLETED");
  }

  @Test
  void failedRefundCanRetryWithTheSameGatewayKeyAndOnlyOneLedgerReversal() throws Exception {
    meals();
    long id = paidOrder();
    long refund =
        call("POST", "/orders/" + id + "/cancel", Map.of("reason", "重试测试"), "family", 200)
            .path("refundId")
            .asLong();
    var gateway = org.mockito.Mockito.mock(com.community.meal.payment.PaymentGateway.class);
    org.mockito.Mockito.when(
            gateway.refund(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
        .thenThrow(new IllegalStateException("模拟网关暂时故障"))
        .thenReturn("MOCK_RETRIED");
    var service =
        new com.community.meal.order.RefundService(
            context.getBean(com.community.meal.common.Db.class),
            context.getBean(com.community.meal.auth.Access.class),
            context.getBean(com.community.meal.order.OrderQueryService.class),
            gateway);
    var operator =
        new com.community.meal.auth.AuthPrincipal(14, "community", List.of("COMMUNITY"), "");
    assertThat(
            service.reviewRefund(operator, refund, "approve", Map.of("note", "批准")).get("status"))
        .isEqualTo("REFUND_FAILED");
    assertThat(service.reviewRefund(operator, refund, "retry", Map.of()).get("status"))
        .isEqualTo("REFUNDED");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subsidy_ledger WHERE order_id=? AND entry_type='REVERSAL'",
                Integer.class,
                id))
        .isEqualTo(1);
    var key = org.mockito.ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(gateway, org.mockito.Mockito.times(2))
        .refund(
            org.mockito.ArgumentMatchers.eq("MOCK_A"),
            key.capture(),
            org.mockito.ArgumentMatchers.any());
    assertThat(key.getAllValues().stream().distinct().count()).isEqualTo(1);
  }

  private long elder, menuItem;
  private String date;

  private void meals() throws Exception {
    elder =
        call("POST", "/elders", Map.of("name", "订餐老人", "siteId", 1), "community", 200)
            .path("id")
            .asLong();
    jdbc.update(
        "INSERT INTO family_binding(family_user_id,elder_id,status)VALUES(13,?,'APPROVED')", elder);
    call(
        "PUT",
        "/elders/" + elder + "/health",
        Map.of("healthDataComplete", true, "allergenDataComplete", true),
        "family",
        200);
    jdbc.update("INSERT INTO subsidy_entitlement(elder_id,is_active)VALUES(?,1)", elder);
    long dish =
        call(
                "POST",
                "/dishes",
                Map.of(
                    "name",
                    "订单测试菜",
                    "originalPrice",
                    "12.50",
                    "elderPrice",
                    "8.50",
                    "ingredients",
                    List.of("米饭"),
                    "allergenDataComplete",
                    true,
                    "caloriesKcal",
                    "300.00"),
                "admin",
                200)
            .path("id")
            .asLong();
    date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1).toString();
    long menu =
        call(
                "POST",
                "/sites/1/menus",
                Map.of(
                    "serviceDate",
                    date,
                    "mealType",
                    "LUNCH",
                    "items",
                    List.of(Map.of("dishId", dish, "plannedQty", 3))),
                "canteen",
                200)
            .path("id")
            .asLong();
    call("POST", "/sites/1/menus/" + menu + "/submit", Map.of(), "canteen", 200);
    var published = call("POST", "/sites/1/menus/" + menu + "/publish", Map.of(), "community", 200);
    menuItem = published.path("items").get(0).path("id").asLong();
  }

  private Map<String, Object> order(String type) {
    return Map.of(
        "elderId",
        elder,
        "siteId",
        1,
        "mealDate",
        date,
        "mealType",
        "LUNCH",
        "deliveryType",
        type,
        "items",
        List.of(Map.of("menuItemId", menuItem, "quantity", 2)));
  }

  private long paidOrder() throws Exception {
    long id = call("POST", "/orders", order("DINE_IN"), "family", 200).path("id").asLong();
    long payment =
        call("POST", "/orders/" + id + "/payments", Map.of("channel", "MOCK_A"), "family", 200)
            .path("id")
            .asLong();
    call("POST", "/dev/payments/" + payment + "/simulate-success", Map.of(), "family", 200);
    call("POST", "/dev/payments/" + payment + "/simulate-success", Map.of(), "family", 200);
    return id;
  }

  @Test
  void orderPaymentPreparationVoucherRedemptionAndSnapshotsFormARealLoop() throws Exception {
    meals();
    long id = paidOrder();
    var details = call("GET", "/orders/" + id, null, "family", 200);
    assertThat(details.path("originalAmount").asText()).isEqualTo("25.00");
    assertThat(details.path("subsidyAmount").asText()).isEqualTo("8.00");
    assertThat(details.path("payableAmount").asText()).isEqualTo("17.00");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM subsidy_ledger WHERE order_id=?", Integer.class, id))
        .isEqualTo(1);
    assertThat(details.path("paymentExpiresAt").asText()).endsWith("Z");
    call("POST", "/orders", order("DINE_IN"), "family", 409);
    call("POST", "/orders/" + id + "/ready", Map.of(), "canteen", 422);
    call("POST", "/orders/" + id + "/start-preparing", Map.of(), "canteen", 200);
    call("POST", "/orders/" + id + "/cancel", Map.of(), "family", 422);
    call("POST", "/orders/" + id + "/ready", Map.of(), "canteen", 200);
    String voucher =
        call("GET", "/orders/" + id + "/pickup-voucher", null, "family", 200)
            .path("voucher")
            .asText();
    call("POST", "/redemptions/confirm", Map.of("voucher", voucher), "canteen", 200);
    call("POST", "/redemptions/confirm", Map.of("voucher", voucher), "community", 422);
    assertThat(jdbc.queryForObject("SELECT status FROM meal_order WHERE id=?", String.class, id))
        .isEqualTo("COMPLETED");
    assertThat(
            call(
                    "GET",
                    "/elders/" + elder + "/nutrition-report?period=week&startDate=" + date,
                    null,
                    "family",
                    200)
                .path("totals")
                .path("caloriesKcal")
                .asText())
        .isEqualTo("600.00");
  }

  @Test
  void fullRefundIsIdempotentAndReversesSubsidyOnce() throws Exception {
    meals();
    long id = paidOrder();
    var cancel = call("POST", "/orders/" + id + "/cancel", Map.of("reason", "行程变化"), "family", 200);
    long refund = cancel.path("refundId").asLong();
    assertThat(refund).isPositive();
    call("POST", "/refunds/" + refund + "/approve", Map.of("note", "同意退款"), "community", 200);
    call("POST", "/refunds/" + refund + "/approve", Map.of("note", "重复回调"), "community", 200);
    assertThat(jdbc.queryForObject("SELECT status FROM meal_order WHERE id=?", String.class, id))
        .isEqualTo("REFUNDED");
    assertThat(
            jdbc.queryForObject(
                "SELECT SUM(amount) FROM subsidy_ledger WHERE order_id=?",
                java.math.BigDecimal.class,
                id))
        .isEqualByComparingTo("0.00");
  }

  @Test
  void expiredUnpaidOrderReleasesItsSlotAndCannotBePaid() throws Exception {
    meals();
    long first = call("POST", "/orders", order("DINE_IN"), "family", 200).path("id").asLong();
    jdbc.update(
        "UPDATE meal_order SET payment_expires_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 SECOND) WHERE"
            + " id=?",
        first);
    call("POST", "/orders/" + first + "/payments", Map.of("channel", "MOCK_A"), "family", 422);
    long replacement = call("POST", "/orders", order("DINE_IN"), "family", 200).path("id").asLong();
    assertThat(replacement).isNotEqualTo(first);
    assertThat(jdbc.queryForObject("SELECT status FROM meal_order WHERE id=?", String.class, first))
        .isEqualTo("CANCELLED");
  }

  @Test
  void laterDishAndQualificationChangesDoNotRewriteOrderSnapshots() throws Exception {
    meals();
    long id = paidOrder();
    jdbc.update(
        "UPDATE dish SET original_price=99,elder_price=90,calories_kcal=999 WHERE id=(SELECT"
            + " dish_id FROM daily_menu_item WHERE id=?)",
        menuItem);
    jdbc.update("UPDATE subsidy_entitlement SET is_active=0 WHERE elder_id=?", elder);
    var saved = call("GET", "/orders/" + id, null, "family", 200);
    assertThat(saved.path("payableAmount").asText()).isEqualTo("17.00");
    assertThat(saved.path("items").get(0).path("nutritionSnapshot").path("caloriesKcal").asText())
        .isEqualTo("300.00");
  }

  @Test
  void requestIdempotencyReturnsOriginalOrderAndRejectsDifferentContents() throws Exception {
    meals();
    String input = json.writeValueAsString(order("DINE_IN"));
    var result =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/v1/orders")
                    .header("Authorization", "Bearer " + tokens.get("family"))
                    .header("Idempotency-Key", "stable-request")
                    .contentType("application/json")
                    .content(input))
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andReturn();
    long id =
        json.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    var repeated =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/v1/orders")
                    .header("Authorization", "Bearer " + tokens.get("family"))
                    .header("Idempotency-Key", "stable-request")
                    .contentType("application/json")
                    .content(input))
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andReturn();
    assertThat(
            json.readTree(repeated.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong())
        .isEqualTo(id);
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/orders")
                .header("Authorization", "Bearer " + tokens.get("family"))
                .header("Idempotency-Key", "stable-request")
                .contentType("application/json")
                .content(json.writeValueAsString(order("TAKEOUT"))))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isUnprocessableEntity());
  }

  @Test
  void combinedRolesPreserveFamilyOrderingOutsideStaffSitesWithoutMislabelingProxy()
      throws Exception {
    meals();
    jdbc.update("INSERT INTO sys_user_role(user_id,role_id)VALUES(13,4)");
    jdbc.update("INSERT INTO sys_user_site(user_id,site_id)VALUES(13,2)");
    call(
        "GET",
        "/elders/" + elder + "/recommendations?siteId=1&date=" + date + "&mealType=LUNCH",
        null,
        "family",
        200);
    var created = call("POST", "/orders", order("DINE_IN"), "family", 200);
    assertThat(created.path("proxyOperatorId").isNull()).isTrue();
    long id = created.path("id").asLong();
    call("GET", "/orders/" + id, null, "family", 200);
    assertThat(
            call("GET", "/orders", null, "family", 200).path("records").get(0).path("id").asLong())
        .isEqualTo(id);
    call("GET", "/sites/1/delivery-list?date=" + date + "&mealType=LUNCH", null, "family", 403);
  }

  @Test
  void serverRejectsPriceTamperingCapacityExcessAndUnknownSafety() throws Exception {
    meals();
    Map<String, Object> input = new HashMap<>(order("DINE_IN"));
    input.put("payableAmount", "0.01");
    call("POST", "/orders", input, "family", 400);
    input.remove("payableAmount");
    input.put("items", List.of(Map.of("menuItemId", menuItem, "quantity", 4)));
    call("POST", "/orders", input, "family", 422);
    jdbc.update("UPDATE elder_health SET health_data_complete=0 WHERE elder_id=?", elder);
    call("POST", "/orders", order("DINE_IN"), "family", 422);
  }
}
