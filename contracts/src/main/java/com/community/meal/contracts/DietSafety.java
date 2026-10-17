package com.community.meal.contracts;

import java.util.*;

/**
 * Stable application port. Implementations retain authorization and transaction responsibilities.
 */
public interface DietSafety {
  String exclusion(Map<String, Object> health, Map<String, Object> item);
}
