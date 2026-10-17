package com.community.meal;

import static org.assertj.core.api.Assertions.*;

import com.community.meal.auth.NewPasswordPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class NewPasswordPolicyTest {
  @Test
  void matchingDemoPasswordIsAllowedOnlyInDev() {
    var environment = new MockEnvironment();
    environment.setActiveProfiles("dev");
    var policy = new NewPasswordPolicy(environment);
    for (String name : new String[] {"admin", "canteen", "family", "community"})
      policy.validate(name, name);
    assertThatThrownBy(() -> policy.validate("admin", "family"))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> policy.validate("ordinary", "ordinary"))
        .isInstanceOf(RuntimeException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"prod", "dev,prod", "test", ""})
  void productionAndOtherProfilesRejectShortPasswords(String profiles) {
    var environment = new MockEnvironment();
    if (!profiles.isEmpty()) environment.setActiveProfiles(profiles.split(","));
    var policy = new NewPasswordPolicy(environment);
    assertThatThrownBy(() -> policy.validate("admin", "admin"))
        .isInstanceOf(RuntimeException.class);
    policy.validate("admin", "Strong-Password-2026!");
  }
}
