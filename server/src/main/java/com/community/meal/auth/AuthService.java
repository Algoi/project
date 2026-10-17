package com.community.meal.auth;


import static com.community.meal.auth.AuthDtos.*;

import com.community.meal.common.ApiException;
import com.community.meal.contracts.AuditEvents;
import com.community.meal.contracts.SiteDirectory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final UserMapper users;
  private final SessionMapper sessions;
  private final SiteDirectory sites;
  private final TokenService tokens;
  private final PasswordEncoder passwords;
  private final AuditEvents audit;
  private final NewPasswordPolicy passwordPolicy;
  private final String dummyHash;

  public AuthService(
      UserMapper users,
      SessionMapper sessions,
      SiteDirectory sites,
      TokenService tokens,
      PasswordEncoder passwords,
      AuditEvents audit,
      NewPasswordPolicy passwordPolicy) {
    this.users = users;
    this.sessions = sessions;
    this.sites = sites;
    this.tokens = tokens;
    this.passwords = passwords;
    this.audit = audit;
    this.passwordPolicy = passwordPolicy;
    this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
  }

  @Transactional(noRollbackFor = ApiException.class)
  public LoginResult login(LoginRequest request) {
    validateBytes(request.password());
    // Login and password changes lock the same user row to serialize credential changes.
    AppUser user = users.lockByUsername(request.username());
    boolean passwordMatches =
        passwords.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
    List<String> roles = user == null ? List.of() : users.roles(user.getId());
    if (user == null || !passwordMatches || !"ACTIVE".equals(user.getStatus()) || roles.isEmpty()) {
      audit.record(null, "LOGIN", null, false, "登录失败");
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "账号或密码错误");
    }
    var issued = tokens.issue(user.getId());
    audit.record(user.getId(), "LOGIN", user.getId().toString(), true, "登录成功");
    return new LoginResult(issued.token(), issued.expiresAt(), view(user, roles));
  }

  public UserView me(AuthPrincipal principal) {
    AppUser user = users.selectById(principal.userId());
    if (user == null || !"ACTIVE".equals(user.getStatus())) throw ApiException.unauthenticated();
    return view(user, users.roles(user.getId()));
  }

  @Transactional
  public void logout(AuthPrincipal principal) {
    sessions.delete(principal.tokenHash());
    audit.record(principal.userId(), "LOGOUT", Long.toString(principal.userId()), true, "退出登录");
  }

  @Transactional
  public UserView updateProfile(AuthPrincipal principal, ProfileRequest request) {
    lockActiveUser(principal);
    users.updateProfile(principal.userId(), request.realName().strip(), request.phone());
    audit.record(
        principal.userId(), "UPDATE_PROFILE", Long.toString(principal.userId()), true, "更新个人信息");
    return me(principal);
  }

  @Transactional
  public void changePassword(AuthPrincipal principal, PasswordRequest request) {
    validateBytes(request.newPassword());
    validateBytes(request.oldPassword());
    AppUser user = lockActiveUser(principal);
    if (!passwords.matches(request.oldPassword(), user.getPasswordHash())) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION", "原密码不正确");
    }
    passwordPolicy.validate(user.getUsername(), request.newPassword());
    if (passwords.matches(request.newPassword(), user.getPasswordHash())) {
      throw ApiException.invalid("新密码不能与原密码相同");
    }
    users.updatePassword(user.getId(), passwords.encode(request.newPassword()));
    sessions.deleteForUser(user.getId());
    audit.record(user.getId(), "CHANGE_PASSWORD", user.getId().toString(), true, "修改密码并撤销全部会话");
  }

  private AppUser lockActiveUser(AuthPrincipal principal) {
    AppUser user = users.lockById(principal.userId());
    if (user == null
        || !"ACTIVE".equals(user.getStatus())
        || !tokens.isActive(principal.tokenHash())) {
      throw ApiException.unauthenticated();
    }
    return user;
  }

  private UserView view(AppUser user, List<String> roles) {
    boolean staff = roles.contains("CANTEEN") || roles.contains("COMMUNITY");
    var allowedSites =
        roles.contains("ADMIN")
            ? sites.listAll(0, Integer.MAX_VALUE)
            : staff
                ? sites.listAssigned(user.getId(), 0, Integer.MAX_VALUE)
                : List.<com.community.meal.contracts.SiteSummary>of();
    String phone = user.getPhone();
    String masked =
        phone == null || phone.length() < 7
            ? null
            : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    return new UserView(
        user.getId(),
        user.getUsername(),
        user.getRealName(),
        masked,
        roles,
        allowedSites,
        users.deniedPermissions(user.getId()));
  }

  private void validateBytes(String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw ApiException.invalid("密码 UTF-8 长度不能超过 72 字节");
  }
}
