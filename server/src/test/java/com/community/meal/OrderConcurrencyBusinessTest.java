package com.community.meal;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.*;

class OrderConcurrencyBusinessTest extends BusinessTestSupport {
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentOrdersCannotOversellOrDuplicateAnElderMeal() throws Exception {
    try {
      String date =
          java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1).toString();
      long
          first =
              call("POST", "/elders", Map.of("name", "并发老人甲", "siteId", 1), "community", 200)
                  .path("id")
                  .asLong(),
          second =
              call("POST", "/elders", Map.of("name", "并发老人乙", "siteId", 1), "community", 200)
                  .path("id")
                  .asLong();
      for (long elder : List.of(first, second)) {
        jdbc.update(
            "INSERT INTO family_binding(family_user_id,elder_id,status)VALUES(13,?,'APPROVED')",
            elder);
        call(
            "PUT",
            "/elders/" + elder + "/health",
            Map.of("healthDataComplete", true, "allergenDataComplete", true),
            "family",
            200);
      }
      long dish =
          call(
                  "POST",
                  "/dishes",
                  Map.of(
                      "name",
                      "并发容量菜",
                      "originalPrice",
                      "10.00",
                      "elderPrice",
                      "8.00",
                      "ingredients",
                      List.of("米饭"),
                      "allergenDataComplete",
                      true),
                  "admin",
                  200)
              .path("id")
              .asLong();
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
      long item =
          call("POST", "/sites/1/menus/" + menu + "/publish", Map.of(), "community", 200)
              .path("items")
              .get(0)
              .path("id")
              .asLong();
      List<Integer> capacity = race(order(first, item, date), order(second, item, date));
      assertThat(capacity).containsExactlyInAnyOrder(200, 422);
      assertThat(jdbc.queryForObject("SELECT SUM(qty) FROM meal_order_item", Integer.class))
          .isEqualTo(2);
      for (long id : jdbc.queryForList("SELECT id FROM meal_order", Long.class))
        call("POST", "/orders/" + id + "/cancel", Map.of(), "community", 200);
      assertThat(race(order(first, item, date), order(first, item, date)))
          .containsExactlyInAnyOrder(200, 409);
    } finally {
      BusinessTestCleanup.clear(jdbc);
    }
  }

  private Map<String, Object> order(long elder, long item, String date) {
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
        "DINE_IN",
        "items",
        List.of(Map.of("menuItemId", item, "quantity", 2)));
  }

  private List<Integer> race(Object first, Object second) throws Exception {
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (Object body : List.of(first, second))
        results.add(
            pool.submit(
                () -> {
                  start.await(5, TimeUnit.SECONDS);
                  return mvc.perform(
                          post("/api/v1/orders")
                              .header("Authorization", "Bearer " + tokens.get("family"))
                              .contentType("application/json")
                              .content(json.writeValueAsString(body)))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      start.countDown();
      return List.of(
          results.get(0).get(15, TimeUnit.SECONDS), results.get(1).get(15, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }
  }
}
