package com.community.meal.payment;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentGateway {
  List<String> channels();

  String refund(String channel, String requestId, BigDecimal amount);

  boolean simulated();

  default PaymentNotice mockNotice(String channel, String paymentNo, BigDecimal amount) {
    throw new IllegalStateException("模拟支付不可用");
  }

  default boolean verify(PaymentNotice notice) {
    return false;
  }
}
