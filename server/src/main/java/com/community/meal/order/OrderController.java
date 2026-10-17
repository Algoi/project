package com.community.meal.order;

import com.community.meal.auth.AuthPrincipal;
import com.community.meal.common.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class OrderController {
  private final OrderService s;

  public OrderController(OrderService s) {
    this.s = s;
  }

  @PostMapping("/orders")
  public ApiResponse<?> create(
      @AuthenticationPrincipal AuthPrincipal p,
      @RequestBody Map<String, Object> in,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return ApiResponse.ok(s.create(p, in, key));
  }

  @GetMapping("/orders")
  public ApiResponse<?> list(
      @AuthenticationPrincipal AuthPrincipal p,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(required = false) Long siteId,
      @RequestParam(defaultValue = "") String date,@RequestParam(defaultValue="")String keyword,@RequestParam(required=false)Long elderId,@RequestParam(defaultValue="")String mealType,@RequestParam(defaultValue="")String dateFrom,@RequestParam(defaultValue="")String dateTo) {
    return ApiResponse.ok(s.list(p, page, size, status, siteId, date,keyword,elderId,mealType,dateFrom,dateTo));
  }

  @GetMapping("/orders/{id}")
  public ApiResponse<?> get(@AuthenticationPrincipal AuthPrincipal p, @PathVariable long id) {
    return ApiResponse.ok(s.get(p, id));
  }

  @PostMapping("/orders/{id}/cancel")
  public ApiResponse<?> cancel(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> in) {
    return ApiResponse.ok(s.cancel(p, id, in));
  }

  @PostMapping("/orders/{id}/{action:start-preparing|ready}")
  public ApiResponse<?> prepare(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @PathVariable String action) {
    return ApiResponse.ok(s.prepare(p, id, action.equals("ready")));
  }

  @GetMapping("/orders/{id}/pickup-voucher")
  public ApiResponse<?> voucher(@AuthenticationPrincipal AuthPrincipal p, @PathVariable long id) {
    return ApiResponse.ok(s.voucher(p, id));
  }

  @GetMapping("/payment/channels")
  public ApiResponse<?> channels(@AuthenticationPrincipal AuthPrincipal actor) {
    return ApiResponse.ok(s.channels(actor));
  }

  @PostMapping("/orders/{id}/payments")
  public ApiResponse<?> pay(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> in) {
    return ApiResponse.ok(s.payment(p, id, Values.text(in, "channel", 30, true)));
  }

  @GetMapping("/orders/{id}/payments")
  public ApiResponse<?> payments(@AuthenticationPrincipal AuthPrincipal p, @PathVariable long id) {
    return ApiResponse.ok(s.payments(p, id));
  }

  @PostMapping("/redemptions/{action:verify|confirm}")
  public ApiResponse<?> redeem(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable String action,
      @RequestBody Map<String, Object> in) {
    return ApiResponse.ok(s.redemption(p, in, action.equals("confirm")));
  }

  @GetMapping("/sites/{siteId}/delivery-list")
  public ApiResponse<?> deliveryList(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long siteId,
      @RequestParam String date,
      @RequestParam(defaultValue = "LUNCH") String mealType,@RequestParam(defaultValue="")String deliveryStatus) {
    if(!deliveryStatus.isBlank())Values.choice(deliveryStatus,"PENDING","OUT_FOR_DELIVERY","DELIVERED");
    var rows=s.deliveryList(p,siteId,Values.date(date),Values.meal(mealType));
    return ApiResponse.ok(deliveryStatus.isBlank()?rows:rows.stream().filter(r->deliveryStatus.equals(r.get("deliveryStatus"))).toList());
  }

  @PatchMapping("/orders/{id}/delivery-status")
  public ApiResponse<?> delivery(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> in) {
    return ApiResponse.ok(
        s.delivery(p, id, Values.choice(in.get("status"), "OUT_FOR_DELIVERY", "DELIVERED")));
  }

  @PatchMapping("/orders/{id}/exception")
  public ApiResponse<?> exception(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> in) {
    return ApiResponse.ok(s.exception(p, id, in));
  }

  @GetMapping("/refunds")
  public ApiResponse<?> refunds(
      @AuthenticationPrincipal AuthPrincipal p,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="")String status,@RequestParam(required=false)Long siteId) {
    return ApiResponse.ok(s.refunds(p, page, size, keyword,status,siteId));
  }

  @GetMapping("/refunds/{id}")
  public ApiResponse<?> refund(@AuthenticationPrincipal AuthPrincipal p, @PathVariable long id) {
    return ApiResponse.ok(s.refund(p, id));
  }

  @PostMapping("/refunds/{id}/{action:approve|reject|retry}")
  public ApiResponse<?> review(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @PathVariable String action,
      @RequestBody Map<String, Object> in) {
    return ApiResponse.ok(s.reviewRefund(p, id, action, in));
  }
}
