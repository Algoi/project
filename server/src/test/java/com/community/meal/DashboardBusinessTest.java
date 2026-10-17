package com.community.meal;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DashboardBusinessTest extends BusinessTestSupport {
  @Test
  void dashboardIsAdministratorOnlyAndEmptyMetricsAreExplicit() throws Exception {
    for (String endpoint :
        new String[] {
          "overview",
          "orders-trend",
          "revenue-subsidy",
          "attendance-forecast",
          "nutrition-stats",
          "recommendation-stats"
        }) {
      call("GET", "/dashboard/" + endpoint, null, "family", 403);
      call("GET", "/dashboard/" + endpoint, null, "admin", 200);
    }
    assertThat(call("GET", "/dashboard/overview", null, "admin", 200).path("netRevenue").asText())
        .isEqualTo("0.00");
    assertThat(
            call("GET", "/dashboard/recommendation-stats", null, "admin", 200)
                .path("conversionRate")
                .isNull())
        .isTrue();
  }
}
