package com.community.meal.config;

import com.community.meal.algorithms.*;
import org.springframework.context.annotation.*;

@Configuration
public class AlgorithmConfiguration {
  @Bean
  ForecastAlgorithm weekdayAverage() {
    return new WeekdayAverageForecast();
  }

  @Bean
  ForecastAlgorithm recentWeightedAverage() {
    return new RecentWeightedForecast();
  }

  @Bean
  RecommendationAlgorithm weightedRecommendation() {
    return new WeightedRecommendation();
  }
}
