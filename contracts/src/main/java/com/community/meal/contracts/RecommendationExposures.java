package com.community.meal.contracts;

import java.util.*;

public interface RecommendationExposures {
  boolean belongs(long event, long elder, long menuItem);
}
