package com.community.meal.payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("(dev | test) & !prod")
public class MockPaymentGateway implements PaymentGateway {
  private final List<String> configured;
  private final byte[] signingKey = new byte[32];

  public MockPaymentGateway(@Value("${app.payment.mock-channels:MOCK_A,MOCK_B}") String channels) {
    configured =
        Arrays.stream(channels.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .distinct()
            .toList();
    if (!List.of("MOCK_A", "MOCK_B").containsAll(configured))
      throw new IllegalArgumentException("模拟支付渠道配置无效");
    new SecureRandom().nextBytes(signingKey);
  }

  public List<String> channels() {
    return configured;
  }

  public String refund(String channel, String requestId, BigDecimal amount) {
    return "MOCK_REFUND_" + requestId;
  }

  public boolean simulated() {
    return true;
  }

  public PaymentNotice mockNotice(String channel, String paymentNo, BigDecimal amount) {
    if (!configured.contains(channel)) throw new IllegalStateException("模拟支付渠道不可用");
    String transaction = "MOCK_" + paymentNo;
    return new PaymentNotice(
        channel, paymentNo, amount, transaction, sign(channel, paymentNo, amount, transaction));
  }

  public boolean verify(PaymentNotice notice) {
    if (notice == null
        || notice.amount() == null
        || notice.signature() == null
        || !configured.contains(notice.channel())) return false;
    String expected =
        sign(notice.channel(), notice.paymentNo(), notice.amount(), notice.transactionNo());
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII),
        notice.signature().getBytes(StandardCharsets.US_ASCII));
  }

  private String sign(String channel, String paymentNo, BigDecimal amount, String transaction) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
      String payload =
          channel + "\n" + paymentNo + "\n" + amount.toPlainString() + "\n" + transaction;
      return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("支付签名服务不可用", e);
    }
  }
}
