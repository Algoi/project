package com.community.meal.auth;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RoleService {
  private final RoleMapper roles;

  public RoleService(RoleMapper roles) {
    this.roles = roles;
  }

  public List<RoleController.RoleView> list() {
    return roles.list();
  }
}
