package com.community.meal.algorithms;

import java.math.*;
import java.util.*;

public record NutrientTarget(BigDecimal min, BigDecimal max) {
  public NutrientTarget {
    Objects.requireNonNull(min);
    Objects.requireNonNull(max);
    if (min.signum() < 0 || max.compareTo(min) < 0) throw new IllegalArgumentException("营养目标区间无效");
  }
}
