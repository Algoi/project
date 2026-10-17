package com.community.meal.payment;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("(!dev & !test) | prod")
public class ExternalPaymentGateway implements PaymentGateway {
  public List<String> channels() {
    return List.of();
  }

  public String refund(String channel, String requestId, BigDecimal amount) {
    throw new IllegalStateException("真实支付渠道尚未配置");
  }

  public boolean simulated() {
    return false;
  }
}
