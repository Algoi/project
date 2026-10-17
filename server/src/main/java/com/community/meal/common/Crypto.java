package com.community.meal.common;

import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Crypto {
  private final byte[] key;

  public Crypto(@Value("${app.encryption-key}") String value) {
    try {
      key = HexFormat.of().parseHex(value);
      if (key.length != 32) throw new Exception();
    } catch (Exception e) {
      throw new IllegalStateException("APP_ENCRYPTION_KEY must contain 64 hexadecimal characters");
    }
  }

  public String encrypt(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      byte[] iv = new byte[12];
      new SecureRandom().nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
      byte[] data = c.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      byte[] all = new byte[iv.length + data.length];
      System.arraycopy(iv, 0, all, 0, iv.length);
      System.arraycopy(data, 0, all, iv.length, data.length);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(all);
    } catch (Exception e) {
      throw new IllegalStateException("Encryption failed");
    }
  }

  public String decrypt(String value) {
    try {
      byte[] all = Base64.getUrlDecoder().decode(value);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(key, "AES"),
          new GCMParameterSpec(128, Arrays.copyOfRange(all, 0, 12)));
      return new String(
          c.doFinal(Arrays.copyOfRange(all, 12, all.length)),
          java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Decryption failed");
    }
  }
}
