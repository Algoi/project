package com.community.meal;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles(
    value = {"prod", "test"},
    inheritProfiles = false)
class ProductionBoundaryTest extends BusinessTestSupport {
  @Test
  void productionNeverExposesMockPaymentOrChannels() throws Exception {
    assertThat(call("GET", "/payment/channels", null, "family", 200).get(0).asText()).isEqualTo("WALLET");
    assertThat(call("GET","/wallet",null,"family",200).path("canTopup").asBoolean()).isFalse();
    call("POST","/dev/wallet/topups/1/confirm",Map.of(),"family",404);
    call("POST", "/dev/payments/1/simulate-success", Map.of(), "family", 404);
  }
}
