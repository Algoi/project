package com.community.meal.payment;

import java.math.BigDecimal;

public record PaymentNotice(
    String channel, String paymentNo, BigDecimal amount, String transactionNo, String signature) {}
