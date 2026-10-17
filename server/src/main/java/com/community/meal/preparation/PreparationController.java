package com.community.meal.preparation;

import com.community.meal.auth.AuthPrincipal;
import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.time.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sites/{siteId}")
public class PreparationController {
  private final PreparationService f;

  public PreparationController(PreparationService f) {
    this.f = f;
  }

  @GetMapping("/preparation-list")
  public ApiResponse<?> preparation(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long siteId,
      @RequestParam String date,
      @RequestParam(defaultValue = "LUNCH") String mealType) {
    return ApiResponse.ok(f.preparation(p, siteId, Values.date(date), Values.meal(mealType)));
  }

  @PutMapping("/preparation-list")
  public ApiResponse<?> adjust(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long siteId,
      @RequestParam String date,
      @RequestParam(defaultValue = "LUNCH") String mealType,
      @RequestBody Map<String, Object> in) {
    return ApiResponse.ok(f.adjust(p, siteId, Values.date(date), Values.meal(mealType), in));
  }
}
