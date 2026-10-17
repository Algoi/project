package com.community.meal.recommendation;

import static com.community.meal.common.Values.*;

import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class RecommendationExposureQueries implements RecommendationExposures {
  private final Db db;

  public RecommendationExposureQueries(Db db) {
    this.db = db;
  }

  public boolean belongs(long event, long elder, long menuItem) {
    return db.count(
            "SELECT COUNT(*) FROM recommendation_event WHERE id=? AND elder_id=? AND"
                + " menu_item_id=?",
            event,
            elder,
            menuItem)
        > 0;
  }
}
