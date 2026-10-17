package com.community.meal.wallet;
import com.community.meal.auth.AuthPrincipal;
import com.community.meal.common.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @Profile("(dev | test) & !prod")
public class MockWalletController {
 private final WalletService service;public MockWalletController(WalletService service){this.service=service;}
 @PostMapping("/api/v1/dev/wallet/topups/{id}/confirm") public ApiResponse<?> confirm(@AuthenticationPrincipal AuthPrincipal actor,@PathVariable long id){return ApiResponse.ok(service.confirm(actor,id));}
}
