package com.community.meal.contracts;

import java.time.LocalDate;
import java.util.*;

public interface BookingSites {
  Map<String, Object> site(long id);

  Set<LocalDate> closedDates(long site, LocalDate from, LocalDate to, String meal);
}
