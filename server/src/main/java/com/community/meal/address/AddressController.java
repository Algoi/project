package com.community.meal.address;
import com.community.meal.auth.AuthPrincipal;import com.community.meal.common.ApiResponse;import java.util.Map;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/addresses") public class AddressController {
 private final AddressService s;public AddressController(AddressService s){this.s=s;}
 @GetMapping public ApiResponse<?> list(@AuthenticationPrincipal AuthPrincipal p,@RequestParam(required=false)Long elderId,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String keyword){return ApiResponse.ok(s.list(p,elderId,page,size,keyword));}
 @PostMapping public ApiResponse<?> create(@AuthenticationPrincipal AuthPrincipal p,@RequestBody Map<String,Object> in){return ApiResponse.ok(s.save(p,null,in));}
 @PutMapping("/{id}") public ApiResponse<?> update(@AuthenticationPrincipal AuthPrincipal p,@PathVariable long id,@RequestBody Map<String,Object> in){return ApiResponse.ok(s.save(p,id,in));}
 @DeleteMapping("/{id}") public ApiResponse<?> delete(@AuthenticationPrincipal AuthPrincipal p,@PathVariable long id){return ApiResponse.ok(s.delete(p,id));}
}
