package com.community.meal.auth;

import com.community.meal.common.ApiException;
import com.community.meal.contracts.SiteDirectory;
import org.springframework.stereotype.Component;

@Component
public class ResourceAccess {
  private final SiteDirectory sites;

  public ResourceAccess(SiteDirectory sites) {
    this.sites = sites;
  }

  public void requireStaffOrAdmin(AuthPrincipal principal) {
    if (!principal.hasRole("ADMIN") && !principal.isStaff()) throw ApiException.forbidden();
  }

  public void requireSite(AuthPrincipal principal, long siteId) {
    requireStaffOrAdmin(principal);
    if (!principal.hasRole("ADMIN") && sites.hasAssignment(principal.userId(), siteId) == 0) {
      throw ApiException.forbidden();
    }
  }
}
