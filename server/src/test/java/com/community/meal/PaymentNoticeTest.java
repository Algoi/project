package com.community.meal;

import static org.assertj.core.api.Assertions.*;

import com.community.meal.payment.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentNoticeTest {
  @Test
  void mockGatewayRejectsForgedOrTamperedNotices() {
    var gateway = new MockPaymentGateway("MOCK_A,MOCK_B");
    var notice = gateway.mockNotice("MOCK_A", "PAY123", new BigDecimal("8.50"));
    assertThat(gateway.verify(notice)).isTrue();
    assertThat(
            gateway.verify(
                new PaymentNotice(
                    "MOCK_A",
                    "PAY123",
                    new BigDecimal("0.01"),
                    notice.transactionNo(),
                    notice.signature())))
        .isFalse();
    assertThat(
            gateway.verify(
                new PaymentNotice(
                    "MOCK_B",
                    "PAY123",
                    notice.amount(),
                    notice.transactionNo(),
                    notice.signature())))
        .isFalse();
    assertThat(
            gateway.verify(
                new PaymentNotice(
                    "MOCK_A",
                    "OTHER",
                    notice.amount(),
                    notice.transactionNo(),
                    notice.signature())))
        .isFalse();
    assertThat(
            gateway.verify(
                new PaymentNotice(
                    "MOCK_A", "PAY123", notice.amount(), notice.transactionNo(), "forged")))
        .isFalse();
  }

  @Test
  void onlyConfiguredMockChannelsAreAvailable() {
    assertThat(new MockPaymentGateway("MOCK_B").channels()).containsExactly("MOCK_B");
    assertThatThrownBy(() -> new MockPaymentGateway("REAL_PAY"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
