package com.community.meal.ticket;

import com.community.meal.auth.AuthPrincipal;
import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.time.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class TicketServiceController {
  private final TicketService s;

  public TicketServiceController(TicketService s) {
    this.s = s;
  }

  @PostMapping("/tickets")
  public ApiResponse<?> ticketCreate(
      @AuthenticationPrincipal AuthPrincipal p, @RequestBody Map<String, Object> in) {
    return ApiResponse.ok(s.createTicket(p, in));
  }

  @GetMapping("/tickets")
  public ApiResponse<?> tickets(
      @AuthenticationPrincipal AuthPrincipal p,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="")String status,@RequestParam(required=false)Long siteId) {
    return ApiResponse.ok(s.tickets(p, page, size, keyword,status,siteId));
  }

  @GetMapping("/tickets/{id}")
  public ApiResponse<?> ticket(@AuthenticationPrincipal AuthPrincipal p, @PathVariable long id) {
    return ApiResponse.ok(s.ticket(p, id));
  }

  @PostMapping("/tickets/{id}/reply")
  public ApiResponse<?> reply(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> in) {
    return ApiResponse.ok(s.reply(p, id, in));
  }

  @PatchMapping("/tickets/{id}/status")
  public ApiResponse<?> status(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> in) {
    return ApiResponse.ok(s.ticketStatus(p, id, Values.str(in.get("status"))));
  }
}
