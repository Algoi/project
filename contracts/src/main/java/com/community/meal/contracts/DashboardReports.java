package com.community.meal.contracts;

import java.time.LocalDate;

public interface DashboardReports {
  Object read(String type, LocalDate from, LocalDate to, Long site);
}
