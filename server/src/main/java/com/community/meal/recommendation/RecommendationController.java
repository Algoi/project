package com.community.meal.recommendation;

import com.community.meal.auth.AuthPrincipal;
import com.community.meal.common.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RecommendationController {
  private final RecommendationService s;
  private final RecommendationSettings settings;

  public RecommendationController(RecommendationService s, RecommendationSettings settings) {
    this.s = s;
    this.settings = settings;
  }

  @GetMapping("/elders/{id}/recommendations")
  public ApiResponse<?> list(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestParam long siteId,
      @RequestParam String date,
      @RequestParam(defaultValue = "LUNCH") String mealType) {
    return ApiResponse.ok(s.recommend(p, id, siteId, Values.date(date), Values.meal(mealType)));
  }

  @GetMapping("/recommendation-config")
  public ApiResponse<?> config(@AuthenticationPrincipal AuthPrincipal p) {
    return ApiResponse.ok(settings.config(p));
  }

  @PutMapping("/recommendation-config")
  public ApiResponse<?> configSave(
      @AuthenticationPrincipal AuthPrincipal p, @RequestBody Map<String, Object> in) {
    return ApiResponse.ok(settings.saveConfig(p, in));
  }

  @GetMapping("/diet-rules")
  public ApiResponse<?> rules(@AuthenticationPrincipal AuthPrincipal p) {
    return ApiResponse.ok(settings.rules(p));
  }

  @PostMapping("/diet-rules")
  public ApiResponse<?> create(
      @AuthenticationPrincipal AuthPrincipal p, @RequestBody Map<String, Object> in) {
    return ApiResponse.ok(settings.saveRule(p, null, in));
  }

  @PutMapping("/diet-rules/{id}")
  public ApiResponse<?> update(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> in) {
    return ApiResponse.ok(settings.saveRule(p, id, in));
  }

  @DeleteMapping("/diet-rules/{id}")
  public ApiResponse<?> delete(@AuthenticationPrincipal AuthPrincipal p, @PathVariable long id) {
    settings.deleteRule(p, id);
    return ApiResponse.ok(null);
  }
}
