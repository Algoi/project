package com.community.meal.contracts;

import java.util.List;

public interface SiteDirectory {
  List<SiteSummary> listAll(long offset, int size);

  List<SiteSummary> listAssigned(long userId, long offset, int size);

  long hasAssignment(long userId, long siteId);
}
