package com.community.meal.contracts;
import java.math.BigDecimal;
/** Funds operations participate in the owning order transaction. */
public interface Wallets {
  void debit(Actor payer,long paymentId,long orderId,BigDecimal amount);
  String refund(long payerId,long paymentId,long refundId,long orderId,BigDecimal amount);
}
