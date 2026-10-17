package com.community.meal.auth;

import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.time.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class UserAdminServiceController {
  private final UserAdminService service;
  private final Access access;

  public UserAdminServiceController(UserAdminService service, Access access) {
    this.service = service;
    this.access = access;
  }

  @GetMapping("/users")
  public ApiResponse<?> users(
      @AuthenticationPrincipal AuthPrincipal p,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "") String keyword) {
    return ApiResponse.ok(service.users(p, page, size, keyword));
  }

  @GetMapping("/users/{id}")
  public ApiResponse<?> user(@AuthenticationPrincipal AuthPrincipal p, @PathVariable long id) {
    return ApiResponse.ok(service.user(p, id));
  }

  @PostMapping("/users")
  public ApiResponse<?> createUser(
      @AuthenticationPrincipal AuthPrincipal p, @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(service.saveUser(p, null, input));
  }

  @PutMapping("/users/{id}")
  public ApiResponse<?> updateUser(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(service.saveUser(p, id, input));
  }

  @PatchMapping("/users/{id}/status")
  public ApiResponse<?> userStatus(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(service.statusUser(p, id, input));
  }

  @PutMapping("/users/{id}/roles")
  public ApiResponse<?> roles(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(service.assignRoles(p, id, input));
  }

  @PutMapping("/users/{id}/sites")
  public ApiResponse<?> sites(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(service.assignSites(p, id, input));
  }

  @PutMapping("/users/{id}/permissions")
  public ApiResponse<?> permissions(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(service.permissions(p, id, input));
  }

  @GetMapping("/permissions")
  public ApiResponse<?> permissionList(@AuthenticationPrincipal AuthPrincipal p) {
    access.role(p, "ADMIN");
    return ApiResponse.ok(UserAdminService.OPERATIONS);
  }

  @PostMapping("/users/{id}/reset-password")
  public ApiResponse<?> reset(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> input) {
    service.resetPassword(p, id, input);
    return ApiResponse.ok(null);
  }
}
