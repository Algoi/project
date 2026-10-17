package com.community.meal.algorithms;

import java.math.*;
import java.time.LocalDate;
import java.util.*;

public record AttendanceSample(LocalDate date, int count) {
  public AttendanceSample {
    Objects.requireNonNull(date);
    if (count < 0) throw new IllegalArgumentException("人数不得为负");
  }
}
