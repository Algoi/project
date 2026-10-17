package com.community.meal.audit;

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
public class AuditQueryServiceController {
  private final AuditQueryService service;
  private final Access access;

  public AuditQueryServiceController(AuditQueryService service, Access access) {
    this.service = service;
    this.access = access;
  }

  @GetMapping("/audit-logs")
  public ApiResponse<?> logs(
      @AuthenticationPrincipal AuthPrincipal p,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "") String action,
      @RequestParam(defaultValue = "") String dateFrom,
      @RequestParam(defaultValue = "") String dateTo) {
    return ApiResponse.ok(service.logs(p, page, size, action, dateFrom, dateTo));
  }
}
