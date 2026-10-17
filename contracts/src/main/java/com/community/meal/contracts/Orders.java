package com.community.meal.contracts;

import java.util.*;

/**
 * Stable application port. Implementations retain authorization and transaction responsibilities.
 */
public interface Orders {
  Map<String, Object> raw(long id, boolean lock);

  void authorize(Actor actor, Map<String, Object> order);

  Map<String, Object> get(Actor actor, long id);
}
