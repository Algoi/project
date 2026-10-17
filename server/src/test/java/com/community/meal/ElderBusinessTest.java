package com.community.meal;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class ElderBusinessTest extends BusinessTestSupport {
  @Test
  void bindingAndSubsidyRequireReviewAndScopeAndRejectSelfApproval() throws Exception {
    long elder =
        call(
                "POST",
                "/elders",
                Map.of("name", "张大爷", "siteId", 1, "birthDate", "1950-01-01"),
                "community",
                200)
            .path("id")
            .asLong();
    call("GET", "/elders/" + elder + "/health", null, "family", 403);
    long binding =
        call("POST", "/family/bindings", Map.of("elderId", elder), "family", 200)
            .path("id")
            .asLong();
    call("POST", "/bindings/" + binding + "/approve", Map.of("note", "现场核验关系"), "canteen", 403);
    call("POST", "/bindings/" + binding + "/approve", Map.of("note", "现场核验关系"), "community", 200);
    call(
        "PUT",
        "/elders/" + elder + "/health",
        Map.of(
            "diseaseCodes",
            List.of("TEST"),
            "allergenIds",
            List.of(),
            "allergenDataComplete",
            true,
            "healthDataComplete",
            true,
            "preferredCategoryIds",
            List.of()),
        "family",
        200);
    assertThat(
            call("GET", "/elders/" + elder + "/health", null, "family", 200)
                .path("healthDataComplete")
                .asBoolean())
        .isTrue();
    long application =
        call("POST", "/subsidy-applications", Map.of("elderId", elder), "family", 200)
            .path("id")
            .asLong();
    call(
        "POST",
        "/subsidy-applications/" + application + "/approve",
        Map.of("note", "材料已核对"),
        "community",
        200);
    assertThat(
            jdbc.queryForObject(
                "SELECT is_active FROM subsidy_entitlement WHERE elder_id=?", Boolean.class, elder))
        .isTrue();
    long own =
        call("POST", "/subsidy-applications", Map.of("elderId", elder), "community", 200)
            .path("id")
            .asLong();
    call(
        "POST",
        "/subsidy-applications/" + own + "/approve",
        Map.of("note", "自己审核"),
        "community",
        422);
  }

  @Test
  void communityCannotCreateOrReadAcrossSitesAndAdminHasNoHealthDefault() throws Exception {
    call("POST", "/elders", Map.of("name", "越权", "siteId", 2), "community", 403);
    jdbc.update("INSERT INTO elder(id,site_id,name)VALUES(100,2,'其他网点老人')");
    call("GET", "/elders/100", null, "community", 403);
    call("GET", "/elders/100/health", null, "admin", 403);
  }

  @Test
  void familyAndCommunityRolesCombineWithoutGrantingForeignSiteStaffPowers() throws Exception {
    long elder =
        call("POST", "/elders", Map.of("name", "兼任角色关联老人", "siteId", 1), "community", 200)
            .path("id")
            .asLong();
    long binding =
        call("POST", "/family/bindings", Map.of("elderId", elder), "family", 200)
            .path("id")
            .asLong();
    call("POST", "/bindings/" + binding + "/approve", Map.of("note", "核对关联"), "community", 200);
    jdbc.update("INSERT INTO sys_user_role(user_id,role_id)VALUES(13,4)");
    jdbc.update("INSERT INTO sys_user_site(user_id,site_id)VALUES(13,2)");
    long app =
        call("POST", "/subsidy-applications", Map.of("elderId", elder), "family", 200)
            .path("id")
            .asLong();
    call("GET", "/subsidy-applications/" + app, null, "family", 200);
    assertThat(
            call("GET", "/subsidy-applications", null, "family", 200)
                .path("records")
                .get(0)
                .path("id")
                .asLong())
        .isEqualTo(app);
    long pending =
        call("POST", "/elders", Map.of("name", "家属跨任职网点申请", "siteId", 1), "family", 200)
            .path("id")
            .asLong();
    assertThat(
            jdbc.queryForObject(
                "SELECT approval_status FROM elder WHERE id=?", String.class, pending))
        .isEqualTo("PENDING");
    call(
        "PUT",
        "/elders/" + elder,
        Map.of("name", "兼任角色关联老人", "siteId", 1, "status", "DISABLED"),
        "family",
        403);
  }

  @Test
  void familyMayRequestNewElderButCannotAccessPrivacyBeforeReview() throws Exception {
    long elder =
        call("POST", "/elders", Map.of("name", "建档申请", "siteId", 1), "family", 200)
            .path("id")
            .asLong();
    call("GET", "/elders/" + elder + "/health", null, "family", 403);
    assertThat(call("GET", "/family/bindings", null, "family", 200).toString()).contains("PENDING");
  }
}
