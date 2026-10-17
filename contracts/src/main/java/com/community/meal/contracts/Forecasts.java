package com.community.meal.contracts;

import java.time.LocalDate;
import java.util.*;

/**
 * Stable application port. Implementations retain authorization and transaction responsibilities.
 */
public interface Forecasts {
  Map<String, Object> calculate(long site, String meal, LocalDate start, int days);
}
