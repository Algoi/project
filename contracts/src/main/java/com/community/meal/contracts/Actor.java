package com.community.meal.contracts;

import java.util.List;

/** Identity supplied by the authenticated boundary; no session or framework types. */
public interface Actor {
  long userId();

  String username();

  List<String> roles();

  default boolean hasRole(String role) {
    return roles().contains(role);
  }

  default boolean isStaff() {
    return hasRole("CANTEEN") || hasRole("COMMUNITY");
  }
}
