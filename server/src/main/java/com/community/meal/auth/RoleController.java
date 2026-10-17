package com.community.meal.auth;

import com.community.meal.common.ApiResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {
  public record RoleView(long id, String code, String name) {}

  private final RoleService roles;

  public RoleController(RoleService roles) {
    this.roles = roles;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<List<RoleView>> list() {
    return ApiResponse.ok(roles.list());
  }
}
