package com.community.meal.contracts;

import java.util.*;

public interface CatalogSnapshots {
  Map<String, Object> snapshot(long dish);

  List<Long> dishesInCategory(Object category);

  List<String> allergenNames(List<Long> ids);
}
