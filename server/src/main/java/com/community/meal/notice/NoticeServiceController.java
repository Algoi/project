package com.community.meal.notice;

import com.community.meal.auth.AuthPrincipal;
import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.time.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class NoticeServiceController {
  private final NoticeService s;

  public NoticeServiceController(NoticeService s) {
    this.s = s;
  }

  @GetMapping("/notices")
  public ApiResponse<?> notices(
      @AuthenticationPrincipal AuthPrincipal p,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "") String keyword) {
    return ApiResponse.ok(s.notices(p, page, size, keyword));
  }

  @GetMapping("/notices/{id}")
  public ApiResponse<?> notice(@AuthenticationPrincipal AuthPrincipal p, @PathVariable long id) {
    var row = s.notice(p, id);
    row.put("receiveRoles", row.get("targetRoles"));
    row.put("siteIds", row.get("targetSites"));
    return ApiResponse.ok(row);
  }

  @PostMapping("/notices")
  public ApiResponse<?> noticeCreate(
      @AuthenticationPrincipal AuthPrincipal p, @RequestBody Map<String, Object> in) {
    return ApiResponse.ok(s.saveNotice(p, null, in));
  }

  @PutMapping("/notices/{id}")
  public ApiResponse<?> noticeUpdate(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> in) {
    return ApiResponse.ok(s.saveNotice(p, id, in));
  }

  @PostMapping("/notices/{id}/{action:publish|revoke}")
  public ApiResponse<?> publish(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @PathVariable String action) {
    return ApiResponse.ok(s.publishNotice(p, id, action.equals("publish")));
  }
}
