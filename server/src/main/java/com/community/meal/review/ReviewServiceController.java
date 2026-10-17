package com.community.meal.review;

import com.community.meal.auth.AuthPrincipal;
import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.time.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ReviewServiceController {
  private final ReviewService s;

  public ReviewServiceController(ReviewService s) {
    this.s = s;
  }

  @PostMapping("/reviews")
  public ApiResponse<?> review(
      @AuthenticationPrincipal AuthPrincipal p, @RequestBody Map<String, Object> in) {
    return ApiResponse.ok(s.review(p, in));
  }

  @GetMapping("/reviews")
  public ApiResponse<?> reviews(
      @AuthenticationPrincipal AuthPrincipal p,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) Integer maxRating,@RequestParam(defaultValue="") String keyword) {
    return ApiResponse.ok(s.reviews(p, null, page, size, maxRating, keyword));
  }

  @GetMapping("/sites/{siteId}/reviews")
  public ApiResponse<?> siteReviews(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long siteId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) Integer maxRating,@RequestParam(defaultValue="") String keyword) {
    return ApiResponse.ok(s.reviews(p, siteId, page, size, maxRating, keyword));
  }
}
