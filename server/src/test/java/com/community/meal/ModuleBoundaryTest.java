package com.community.meal;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;

/** Executable dependency rules: run without a database using -Dtest=ModuleBoundaryTest. */
class ModuleBoundaryTest {
  private static final Path ROOT =
      Files.isDirectory(Path.of("algorithms")) ? Path.of("") : Path.of("..");

  @Test
  void calculationAndContractsHaveNoApplicationOrFrameworkDependencies() throws Exception {
    for (String module : List.of("algorithms", "contracts")) {
      try (var files = Files.walk(ROOT.resolve(module + "/src/main/java"))) {
        for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
          String source = Files.readString(file);
          Matcher imports = Pattern.compile("import\\s+(?:static\\s+)?([^;]+);").matcher(source);
          while (imports.find())
            assertThat(imports.group(1))
                .as(file.toString())
                .matches(
                    "(?:java\\..*|com\\.community\\.meal\\.(?:algorithms|contracts|payment)\\..*)");
        }
      }
      String pom = Files.readString(ROOT.resolve(module + "/pom.xml"));
      assertThat(pom).doesNotContain("spring-boot-starter", "mysql", "mybatis");
    }
  }

  @Test
  void businessModulesUsePublicContractsInsteadOfForeignImplementations() throws Exception {
    Path base = ROOT.resolve("server/src/main/java/com/community/meal");
    Set<String> infrastructure = Set.of("config", "dev", "common");
    Set<String> authApi = Set.of("Access", "AuthPrincipal", "ResourceAccess");
    try (var files = Files.walk(base)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        String owner = base.relativize(file).getName(0).toString();
        if (infrastructure.contains(owner) || owner.endsWith(".java")) continue;
        String source = Files.readString(file);
        Matcher references =
            Pattern.compile("com\\.community\\.meal\\.([a-z]+)\\.([A-Z][A-Za-z0-9]*|\\*)")
                .matcher(source);
        while (references.find()) {
          String target = references.group(1), type = references.group(2);
          boolean sameContext =
              Set.of("order", "payment").contains(owner)
                  && Set.of("order", "payment").contains(target);
          boolean publicApi =
              target.equals("contracts")
                  || target.equals("common")
                  || target.equals("auth") && authApi.contains(type)
                  || target.equals("algorithms")
                      && Set.of("recommendation", "forecast").contains(owner);
          assertThat(target.equals(owner) || sameContext || publicApi)
              .as(file + " depends on " + target + "." + type + "; publish a contract instead")
              .isTrue();
        }
      }
    }
  }

  @Test
  void commandsOnlyWriteTablesOwnedByTheirBusinessModule() throws Exception {
    Map<String, Set<String>> ownership =
        Map.ofEntries(
            Map.entry(
                "auth",
                Set.of(
                    "sys_user",
                    "sys_user_role",
                    "sys_user_site",
                    "sys_user_permission",
                    "auth_session")),
            Map.entry("site", Set.of("service_site", "site_closed_day")),
            Map.entry("catalog", Set.of("dish", "dish_category", "allergen", "dish_allergen")),
            Map.entry(
                "elder",
                Set.of(
                    "elder",
                    "elder_health","binding_history","subsidy_history",
                    "family_binding",
                    "subsidy_application",
                    "subsidy_entitlement")),
            Map.entry("menu", Set.of("daily_menu", "daily_menu_item","menu_template")),
            Map.entry("forecast", Set.of("forecast_result")),
            Map.entry("preparation", Set.of("preparation_item")),
            Map.entry(
                "recommendation",
                Set.of("recommendation_config", "recommendation_event", "diet_rule")),
            Map.entry(
                "order",
                Set.of(
                    "order_event",
                    "meal_order",
                    "meal_order_item",
                    "payment_record",
                    "refund_record",
                    "subsidy_ledger",
                    "delivery_record",
                    "redemption",
                    "attendance_daily")),
            Map.entry("notice", Set.of("notice")),
            Map.entry("review", Set.of("dish_review")),
            Map.entry("ticket", Set.of("feedback_ticket", "ticket_reply")),
            Map.entry("messages",Set.of("inbox_message")),
            Map.entry("address",Set.of("delivery_address")),
            Map.entry("wallet", Set.of("wallet_account","wallet_topup","wallet_ledger")),
            Map.entry("audit", Set.of("audit_log")));
    Path base = ROOT.resolve("server/src/main/java/com/community/meal");
    int checked = 0;
    try (var files = Files.walk(base)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        String module = base.relativize(file).getName(0).toString();
        if (Set.of("common", "dev").contains(module)) continue;
        Matcher commands =
            Pattern.compile("\"(?:INSERT(?: IGNORE)? INTO|UPDATE|DELETE FROM)\\s+([a-z_]+)")
                .matcher(Files.readString(file));
        while (commands.find()) {
          checked++;
          assertThat(ownership.getOrDefault(module, Set.of()))
              .as(file + " writes " + commands.group(1))
              .contains(commands.group(1));
        }
      }
    }
    assertThat(checked).as("SQL ownership checks must inspect actual commands").isGreaterThan(30);
  }
}
