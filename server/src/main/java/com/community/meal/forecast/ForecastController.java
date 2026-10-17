package com.community.meal.forecast;

import com.community.meal.auth.AuthPrincipal;
import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.time.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sites/{siteId}")
public class ForecastController {
  private final ForecastService f;

  public ForecastController(ForecastService f) {
    this.f = f;
  }

  @GetMapping("/forecasts")
  public ApiResponse<?> forecast(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long siteId,
      @RequestParam String startDate,
      @RequestParam(defaultValue = "7") int days,
      @RequestParam(defaultValue = "LUNCH") String mealType) {
    return ApiResponse.ok(
        f.predict(p, siteId, Values.meal(mealType), Values.date(startDate), days));
  }

  @GetMapping("/attendance-history")
  public ApiResponse<?> history(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long siteId,
      @RequestParam(defaultValue = "LUNCH") String mealType) {
    return ApiResponse.ok(f.history(p, siteId, Values.meal(mealType)));
  }
}
