package com.community.meal.wallet;
import com.community.meal.auth.AuthPrincipal;
import com.community.meal.common.ApiResponse;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/wallet")
public class WalletController {
 private final WalletService service;public WalletController(WalletService service){this.service=service;}
 @GetMapping public ApiResponse<?> summary(@AuthenticationPrincipal AuthPrincipal actor){return ApiResponse.ok(service.summary(actor));}
 @GetMapping("/transactions") public ApiResponse<?> ledger(@AuthenticationPrincipal AuthPrincipal actor,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.ok(service.ledger(actor,page,size));}
 @PostMapping("/topups") public ApiResponse<?> topup(@AuthenticationPrincipal AuthPrincipal actor,@RequestBody Map<String,Object> input){return ApiResponse.ok(service.topup(actor,input));}
}
