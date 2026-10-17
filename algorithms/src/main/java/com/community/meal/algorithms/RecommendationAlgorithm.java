package com.community.meal.algorithms;

import java.math.*;
import java.util.*;

/** Receives only candidates that already passed the application's mandatory dietary checks. */
public interface RecommendationAlgorithm extends VersionedAlgorithm {
  List<RankedCandidate> rank(
      List<RecommendationCandidate> candidates, RecommendationParameters parameters);
}
