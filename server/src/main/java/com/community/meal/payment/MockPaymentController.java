package com.community.meal.payment;

import com.community.meal.auth.AuthPrincipal;
import com.community.meal.common.ApiResponse;
import com.community.meal.order.OrderService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("(dev | test) & !prod")
public class MockPaymentController {
  private final OrderService orders;

  public MockPaymentController(OrderService orders) {
    this.orders = orders;
  }

  @PostMapping("/api/v1/dev/payments/{id}/simulate-success")
  public ApiResponse<?> success(@AuthenticationPrincipal AuthPrincipal p, @PathVariable long id) {
    return ApiResponse.ok(orders.simulate(p, id));
  }
}
