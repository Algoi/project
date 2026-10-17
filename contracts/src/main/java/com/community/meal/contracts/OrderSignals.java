package com.community.meal.contracts;

import java.time.LocalDate;
import java.util.*;

public interface OrderSignals {
  List<Long> itemIdsForDish(long dish);

  boolean selectedDish(long elder, long dish, LocalDate since);

  boolean selectedAnyDish(long elder, List<Long> dishes, LocalDate since);

  List<Map<String, Object>> nutritionRows(long elder, LocalDate start, LocalDate end);

  List<Map<String, Object>> mealHistory(long elder);
}
