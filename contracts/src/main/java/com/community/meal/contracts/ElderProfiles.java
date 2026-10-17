package com.community.meal.contracts;

import java.util.*;

/**
 * Stable application port. Implementations retain authorization and transaction responsibilities.
 */
public interface ElderProfiles {
  Map<String, Object> row(long id);

  Map<String, Object> rowForUpdate(long id);

  boolean subsidized(long id);
  default boolean subsidized(long id,java.time.LocalDate mealDate){return subsidized(id);}

  Map<String, Object> dietarySummary(long id);

  Map<String, Object> health(Actor actor, long id);
}
