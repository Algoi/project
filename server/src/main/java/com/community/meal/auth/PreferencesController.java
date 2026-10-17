package com.community.meal.auth;

import com.community.meal.common.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/preferences")
public class PreferencesController {
  private final Db db;

  public PreferencesController(Db db) {
    this.db = db;
  }

  @GetMapping
  public ApiResponse<?> get(@AuthenticationPrincipal AuthPrincipal p) {
    return ApiResponse.ok(
        db.one("SELECT preferences_json FROM sys_user WHERE id=?", p.userId()).get("preferences"));
  }

  @PutMapping
  public ApiResponse<?> save(
      @AuthenticationPrincipal AuthPrincipal p, @RequestBody Map<String, Object> input) {
    Values.fields(input, "preferredCategoryIds", "largeText");
    var data =
        Values.map(
            "preferredCategoryIds",
            Values.ids(input.get("preferredCategoryIds")),
            "largeText",
            Values.yes(input.get("largeText")));
    db.exec("UPDATE sys_user SET preferences_json=? WHERE id=?", db.json(data), p.userId());
    db.audit(p, "PERSONAL_PREFERENCES", "USER", p.userId());
    return ApiResponse.ok(data);
  }
}
