package com.community.meal.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.*;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
  private final SessionMapper sessions;
  private final Duration lifetime;
  private final SecureRandom random = new SecureRandom();

  public TokenService(
      SessionMapper sessions, @Value("${app.auth.token-lifetime}") Duration lifetime) {
    this.sessions = sessions;
    this.lifetime = lifetime;
  }

  public record IssuedToken(String token, Instant expiresAt) {}

  public IssuedToken issue(long userId) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant expiry = Instant.now().plus(lifetime);
    sessions.insert(hash(raw), userId, LocalDateTime.ofInstant(expiry, ZoneOffset.UTC));
    return new IssuedToken(raw, expiry);
  }

  public AppUser resolve(String raw) {
    if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) return null;
    return sessions.findActiveUser(hash(raw), LocalDateTime.now(ZoneOffset.UTC));
  }

  public boolean isActive(String hash) {
    return sessions.findActiveUser(hash, LocalDateTime.now(ZoneOffset.UTC)) != null;
  }

  public static String hash(String raw) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  @Scheduled(fixedDelay = 3600000)
  public void purgeExpired() {
    sessions.deleteExpired(LocalDateTime.now(ZoneOffset.UTC));
  }
}
