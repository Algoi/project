package com.community.meal.contracts;

import java.time.LocalDate;
import java.util.*;

public interface OrderDemand {
  long occupied(long item, boolean locking);

  long confirmed(long item);

  Map<String, Object> preparationHistory(long site, LocalDate day, String meal);

  long historicalDishQty(long site, LocalDate day, String meal, long dish);

  List<Map<String, Object>> attendance(long site, String meal, LocalDate from, LocalDate until);
}
