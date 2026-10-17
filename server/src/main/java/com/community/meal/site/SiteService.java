package com.community.meal.site;

import com.community.meal.auth.AuthPrincipal;
import com.community.meal.auth.ResourceAccess;
import com.community.meal.common.*;
import com.community.meal.contracts.SiteSummary;
import org.springframework.stereotype.Service;

@Service
public class SiteService {
  private final SiteMapper sites;
  private final ResourceAccess access;

  public SiteService(SiteMapper sites, ResourceAccess access) {
    this.sites = sites;
    this.access = access;
  }

  public PageResult<SiteSummary> list(AuthPrincipal principal, int page, int size, String keyword) {
    access.requireStaffOrAdmin(principal);
    if(page<1||size<1||size>100)throw ApiException.invalid("分页参数不正确");
    return new PageResult<>(sites.search(principal.userId(),principal.hasRole("ADMIN"),keyword.trim(),(long)(page-1)*size,size),sites.searchCount(principal.userId(),principal.hasRole("ADMIN"),keyword.trim()));
  }

  public SiteSummary get(AuthPrincipal principal, long id) {
    access.requireSite(principal, id);
    SiteSummary site = sites.find(id);
    if (site == null) throw ApiException.notFound();
    return site;
  }
}
