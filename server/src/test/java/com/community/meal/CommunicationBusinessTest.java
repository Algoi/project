package com.community.meal;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class CommunicationBusinessTest extends BusinessTestSupport {
  @Test
  void noticesRespectBothRoleAndSiteAndCannotBeGuessed() throws Exception {
    long id =
        call(
                "POST",
                "/notices",
                Map.of(
                    "title",
                    "限定通知",
                    "type",
                    "ACTIVITY",
                    "content",
                    "仅第一网点食堂可见",
                    "receiveRoles",
                    List.of("CANTEEN"),
                    "siteIds",
                    List.of(1)),
                "admin",
                200)
            .path("id")
            .asLong();
    call("GET", "/notices/" + id, null, "canteen", 403);
    call("POST", "/notices/" + id + "/publish", Map.of(), "admin", 200);
    call("GET", "/notices/" + id, null, "canteen", 200);
    call("GET", "/notices/" + id, null, "family", 403);
    call("POST", "/notices/" + id + "/revoke", Map.of(), "admin", 200);
    call("GET", "/notices/" + id, null, "canteen", 403);
  }

  @Test
  void ticketsArePrivateAndEscalationAllowsAdministratorReply() throws Exception {
    long id =
        call(
                "POST",
                "/tickets",
                Map.of("siteId", 1, "type", "COMPLAINT", "content", "测试问题"),
                "family",
                200)
            .path("id")
            .asLong();
    call("GET", "/tickets/" + id, null, "canteen", 403);
    call("GET", "/tickets/" + id, null, "admin", 403);
    call("POST", "/tickets/" + id + "/reply", Map.of("content", "已经受理"), "community", 200);
    call("PATCH", "/tickets/" + id + "/status", Map.of("status", "ESCALATED"), "community", 200);
    call("POST", "/tickets/" + id + "/reply", Map.of("content", "管理员跟进"), "admin", 200);
    call("PATCH", "/tickets/" + id + "/status", Map.of("status", "RESOLVED"), "admin", 200);
    assertThat(call("GET", "/tickets/" + id, null, "family", 200).path("replies").size())
        .isEqualTo(2);
  }

  @Test
  void reviewsRequireCompletedConsumptionAndAreUnique() throws Exception {
    call(
        "POST",
        "/reviews",
        Map.of("orderItemId", 999, "rating", 5, "content", "未消费不可评价"),
        "family",
        404);
    call(
        "POST",
        "/reviews",
        Map.of("orderItemId", 999, "rating", 8, "content", "非法分数"),
        "family",
        400);
  }
}
