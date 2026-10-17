package com.community.meal.contracts;

import java.time.LocalDate;
import java.util.*;

/**
 * Stable application port. Implementations retain authorization and transaction responsibilities.
 */
public interface MenuCatalog {
  Map<String, Object> get(Actor actor, long site, LocalDate date, String meal);

  List<Map<String, Object>> items(long menu);

  long occupiedCurrent(long item);

  Map<String, Object> lockItem(long item);
}
