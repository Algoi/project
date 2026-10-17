package com.community.meal.nutrition;

import static com.community.meal.common.Values.*;

import com.community.meal.auth.Access;
import com.community.meal.auth.AuthPrincipal;
import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

@Service
public class NutritionService {
  private final Db db;
  private final ElderProfiles elders;
  private final Access access;
  private final OrderSignals orders;
  public static final List<String> NUTRIENTS =
      List.of("caloriesKcal", "proteinGrams", "fatGrams", "carbGrams", "sodiumMg");

  public NutritionService(Db db, ElderProfiles elders, Access access, OrderSignals orders) {
    this.db = db;
    this.elders = elders;
    this.access = access;
    this.orders = orders;
  }

  public Map<String, Object> report(AuthPrincipal p, long id, String period, LocalDate start) {
    access.elder(p, elders.row(id), true);
    choice(period, "week", "month");
    LocalDate end = period.equals("week") ? start.plusDays(7) : start.plusMonths(1);
    var rows = orders.nutritionRows(id, start, end);
    Map<String, BigDecimal> totals = new LinkedHashMap<>();
    Map<String, Map<String, Object>> daily = new TreeMap<>();
    int missing = 0;
    Set<Long> orders = new HashSet<>();
    for (var row : rows) {
      orders.add(num(row.get("orderId")));
      String date = str(row.get("mealDate"));
      Map<String, Object> day = daily.computeIfAbsent(date, key -> map("date", key));
      Map<String, Object> nutrition =
          row.get("nutritionSnapshot") == null ? Map.of() : object(row.get("nutritionSnapshot"));
      for (String nutrient : NUTRIENTS) {
        if (nutrition.get(nutrient) == null) {
          missing++;
          continue;
        }
        BigDecimal amount =
            decimal(nutrition.get(nutrient))
                .multiply(BigDecimal.valueOf(num(row.get("qty"))))
                .setScale(2);
        totals.merge(nutrient, amount, BigDecimal::add);
        day.put(nutrient, decimal(day.getOrDefault(nutrient, BigDecimal.ZERO)).add(amount));
      }
    }
    var result =
        map(
            "elderId",
            id,
            "period",
            period,
            "startDate",
            start.toString(),
            "endDate",
            end.minusDays(1).toString(),
            "totals",
            totals,
            "days",
            new ArrayList<>(daily.values()),
            "mealCount",
            orders.size(),
            "missingNutrientValues",
            missing,
            "empty",
            rows.isEmpty(),
            "notice",
            "仅统计本系统已核销或送达餐食的营养估算，未计算其他饮食及剩餐，不能视为完整每日摄入或医学诊断");
    return result;
  }

  public List<Map<String, Object>> history(AuthPrincipal p, long id) {
    access.elder(p, elders.row(id), true);
    return orders.mealHistory(id);
  }

  @RestController
  @RequestMapping("/api/v1/elders/{id}")
  public static class Controller {
    private final NutritionService service;

    public Controller(NutritionService service) {
      this.service = service;
    }

    @GetMapping("/nutrition-report")
    public ApiResponse<?> report(
        @AuthenticationPrincipal AuthPrincipal p,
        @PathVariable long id,
        @RequestParam(defaultValue = "week") String period,
        @RequestParam String startDate) {
      return ApiResponse.ok(service.report(p, id, period, date(startDate)));
    }

    @GetMapping("/meal-history")
    public ApiResponse<?> history(@AuthenticationPrincipal AuthPrincipal p, @PathVariable long id) {
      return ApiResponse.ok(service.history(p, id));
    }
  }
}
