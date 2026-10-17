package com.community.meal.auth;

import static com.community.meal.auth.AuthDtos.*;

import com.community.meal.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/login")
  public ApiResponse<LoginResult> login(@Valid @RequestBody LoginRequest request) {
    return ApiResponse.ok(auth.login(request));
  }

  @GetMapping("/me")
  public ApiResponse<UserView> me(@AuthenticationPrincipal AuthPrincipal principal) {
    return ApiResponse.ok(auth.me(principal));
  }

  @PostMapping("/logout")
  public ApiResponse<Void> logout(@AuthenticationPrincipal AuthPrincipal principal) {
    auth.logout(principal);
    return ApiResponse.ok(null);
  }

  @PutMapping("/profile")
  public ApiResponse<UserView> profile(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody ProfileRequest request) {
    return ApiResponse.ok(auth.updateProfile(principal, request));
  }

  @PutMapping("/password")
  public ApiResponse<Void> password(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody PasswordRequest request) {
    auth.changePassword(principal, request);
    return ApiResponse.ok(null);
  }
}
