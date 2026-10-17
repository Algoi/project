package com.community.meal.contracts;

import java.math.BigDecimal;
import java.util.*;

public interface ReviewRatings {
  BigDecimal averageForDish(long dish);
}
