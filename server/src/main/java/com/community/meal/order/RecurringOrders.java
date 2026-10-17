package com.community.meal.order;

import static com.community.meal.common.Values.*;

import com.community.meal.auth.Access;
import com.community.meal.auth.AuthPrincipal;
import com.community.meal.common.*;
import com.community.meal.contracts.MenuCatalog;
import java.time.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

@Service
public class RecurringOrders {
  private final OrderService orders;
  private final Access access;
  private final MenuCatalog menus;

  public RecurringOrders(OrderService orders, Access access, MenuCatalog menus) {
    this.orders = orders;
    this.access = access;
    this.menus = menus;
  }

  public List<Map<String, Object>> create(AuthPrincipal p, Map<String, Object> input) {
    access.role(p, "COMMUNITY");
    LocalDate start = date(input.get("dateFrom")), end = date(input.get("dateTo"));
    if (end.isBefore(start) || end.isAfter(start.plusDays(6)))
      throw ApiException.invalid("周期范围最多 7 天");
    List<Map<String, Object>> results = new ArrayList<>();
    for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
      try {
        Map<String, Object> order = new LinkedHashMap<>(input);
        order.remove("dateFrom");
        order.remove("dateTo");
        order.put("mealDate", day.toString());
        var menu = menus.get(p, id(input.get("siteId")), day, meal(input.get("mealType")));
        List<Map<String, Object>> selections = new ArrayList<>();
        for (Object value : array(input.get("items"))) {
          var rule = object(value);
          long dish = id(rule.get("dishId"));
          var found =
              array(menu.get("items")).stream()
                  .map(Values::object)
                  .filter(v -> num(v.get("dishId")) == dish)
                  .findFirst()
                  .orElseThrow(() -> new IllegalArgumentException("该日没有所选菜品"));
          selections.add(map("menuItemId", found.get("id"), "quantity", rule.get("quantity")));
        }
        order.put("items", selections);
        results.add(
            map("date", day.toString(), "success", true, "order", orders.create(p, order, null)));
      } catch (org.springframework.dao.DataIntegrityViolationException e) {
        results.add(
            map("date", day.toString(), "success", false, "message", "该日已有有效订单或可用份数已变化，请刷新后核对"));
      } catch (ApiException | IllegalArgumentException e) {
        results.add(map("date", day.toString(), "success", false, "message", e.getMessage()));
      }
    }
    return results;
  }

  @RestController
  public static class Controller {
    private final RecurringOrders service;

    public Controller(RecurringOrders service) {
      this.service = service;
    }

    @PostMapping("/api/v1/proxy-orders/recurring")
    public ApiResponse<?> create(
        @AuthenticationPrincipal AuthPrincipal p, @RequestBody Map<String, Object> in) {
      return ApiResponse.ok(service.create(p, in));
    }
  }
}
