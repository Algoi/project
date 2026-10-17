package com.community.meal.site;

import com.community.meal.auth.AuthPrincipal;
import com.community.meal.common.*;
import com.community.meal.contracts.SiteSummary;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sites")
public class SiteController {
  private final SiteService service;

  public SiteController(SiteService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<PageResult<SiteSummary>> list(
      @AuthenticationPrincipal AuthPrincipal principal,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, @RequestParam(defaultValue="") String keyword) {
    return ApiResponse.ok(service.list(principal, page, size, keyword));
  }

  @GetMapping("/{id}")
  public ApiResponse<SiteSummary> get(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable @Positive long id) {
    return ApiResponse.ok(service.get(principal, id));
  }
}
