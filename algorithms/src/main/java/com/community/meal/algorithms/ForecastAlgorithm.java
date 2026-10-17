package com.community.meal.algorithms;

import java.math.*;
import java.util.*;

public interface ForecastAlgorithm extends VersionedAlgorithm {
  List<ForecastPoint> forecast(ForecastRequest request);
}
