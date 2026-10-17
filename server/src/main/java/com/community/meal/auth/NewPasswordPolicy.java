package com.community.meal.auth;

import com.community.meal.common.ApiException;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class NewPasswordPolicy {
  private static final Set<String> DEMO_NAMES = Set.of("admin", "canteen", "family", "community");
  private final Environment environment;

  public NewPasswordPolicy(Environment environment) {
    this.environment = environment;
  }

  public void validate(String username, String newPassword) {
    boolean localDemoPassword =
        environment.acceptsProfiles(Profiles.of("dev & !prod"))
            && DEMO_NAMES.contains(username)
            && username.equals(newPassword);
    if (!localDemoPassword && newPassword.length() < 12) {
      throw ApiException.invalid("新密码至少需要 12 个字符");
    }
  }
}
