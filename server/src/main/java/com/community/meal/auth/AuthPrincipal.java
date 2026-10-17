package com.community.meal.auth;

import java.util.List;

public record AuthPrincipal(long userId, String username, List<String> roles, String tokenHash)
    implements com.community.meal.contracts.Actor {
  public boolean hasRole(String role) {
    return roles.contains(role);
  }

  public boolean isStaff() {
    return hasRole("CANTEEN") || hasRole("COMMUNITY");
  }
}
