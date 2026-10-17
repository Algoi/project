package com.community.meal.site;

import com.community.meal.auth.Access;
import com.community.meal.auth.AuthPrincipal;
import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.time.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SiteAdministrationController {
  private final SiteAdministration service;
  private final Access access;

  public SiteAdministrationController(SiteAdministration service, Access access) {
    this.service = service;
    this.access = access;
  }

  @GetMapping("/site-settings/{id}")
  public ApiResponse<?> siteSettings(
      @AuthenticationPrincipal AuthPrincipal p, @PathVariable long id) {
    return ApiResponse.ok(service.siteDetails(p, id));
  }

  @PostMapping("/sites")
  public ApiResponse<?> siteCreate(
      @AuthenticationPrincipal AuthPrincipal p, @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(service.saveSite(p, null, input));
  }

  @PutMapping("/sites/{id}")
  public ApiResponse<?> siteUpdate(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(service.saveSite(p, id, input));
  }

  @PatchMapping("/sites/{id}/status")
  public ApiResponse<?> siteStatus(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(service.statusSite(p, id, input));
  }

  @GetMapping("/sites/{id}/closed-dates")
  public ApiResponse<?> closed(@AuthenticationPrincipal AuthPrincipal p, @PathVariable long id) {
    return ApiResponse.ok(service.closedDates(p, id));
  }

  @PutMapping("/sites/{id}/closed-dates")
  public ApiResponse<?> closedSave(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(service.saveClosedDates(p, id, input));
  }
}
