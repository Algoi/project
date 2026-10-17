package com.community.meal.dashboard;

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
public class DashboardService {
  private final Access access;
  private final DashboardReports reports;

  public DashboardService(Access access, DashboardReports reports) {
    this.access = access;
    this.reports = reports;
  }

  public Object get(AuthPrincipal p, String type, LocalDate from, LocalDate to, Long site) {
    access.role(p, "ADMIN");
    if (to.isBefore(from) || to.isAfter(from.plusDays(366)))
      throw ApiException.invalid("统计区间最长 367 天");
    return reports.read(type, from, to, site);
  }

  @RestController
  @RequestMapping("/api/v1/dashboard")
  public static class Controller {
    private final DashboardService service;

    public Controller(DashboardService service) {
      this.service = service;
    }

    @GetMapping(
        "/{type:overview|orders-trend|revenue-subsidy|attendance-forecast|nutrition-stats|recommendation-stats}")
    public ApiResponse<?> get(
        @AuthenticationPrincipal AuthPrincipal p,
        @PathVariable String type,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo,
        @RequestParam(required = false) Long siteId) {
      LocalDate
          to =
              dateTo == null
                  ? LocalDate.now(com.community.meal.common.BusinessTime.ZONE)
                  : date(dateTo),
          from = dateFrom == null ? to.minusDays(29) : date(dateFrom);
      return ApiResponse.ok(service.get(p, type, from, to, siteId));
    }
  }
}
