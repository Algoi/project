package com.community.meal.common;

import com.community.meal.algorithms.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The application selects registered pure algorithms; it does not implement their calculations. */
@Component
public class AlgorithmSelection {
  private final ForecastAlgorithm forecast;
  private final RecommendationAlgorithm recommendation;

  public AlgorithmSelection(
      List<ForecastAlgorithm> forecasts,
      List<RecommendationAlgorithm> recommendations,
      @Value("${app.algorithms.forecast:weekday_average_v1}") String forecastId,
      @Value("${app.algorithms.recommendation:weighted_score_v1}") String recommendationId) {
    forecast = new AlgorithmRegistry<>(forecasts).select(forecastId);
    recommendation = new AlgorithmRegistry<>(recommendations).select(recommendationId);
  }

  public ForecastAlgorithm forecast() {
    return forecast;
  }

  public RecommendationAlgorithm recommendation() {
    return recommendation;
  }
}
