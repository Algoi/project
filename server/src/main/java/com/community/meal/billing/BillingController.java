package com.community.meal.billing;
import com.community.meal.auth.AuthPrincipal;
import com.community.meal.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/billing") public class BillingController {
 private final BillingService service;public BillingController(BillingService service){this.service=service;}
 @GetMapping public ApiResponse<?> statement(@AuthenticationPrincipal AuthPrincipal actor,@RequestParam(defaultValue="")String dateFrom,@RequestParam(defaultValue="")String dateTo,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.ok(service.statement(actor,dateFrom,dateTo,keyword,page,size));}
}
