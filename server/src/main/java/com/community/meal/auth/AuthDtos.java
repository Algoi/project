package com.community.meal.auth;

import com.community.meal.contracts.SiteSummary;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class AuthDtos {
  private AuthDtos() {}

  public record LoginRequest(
      @NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 72) String password) {}

  public record ProfileRequest(
      @NotBlank @Size(max = 80) String realName,
      @Pattern(regexp = "^\\+?[0-9 -]{6,30}$") String phone) {}

  public record PasswordRequest(
      @NotBlank @Size(max = 72) String oldPassword, @NotBlank @Size(max = 72) String newPassword) {}

  public record UserView(
      long id,
      String username,
      String realName,
      String phoneMasked,
      List<String> roles,
      List<SiteSummary> sites,
      List<String> deniedPermissions) {}

  public record LoginResult(String token, Instant expiresAt, UserView user) {}
}
