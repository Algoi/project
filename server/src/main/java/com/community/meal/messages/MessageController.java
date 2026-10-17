package com.community.meal.messages;
import com.community.meal.auth.AuthPrincipal;import com.community.meal.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/messages")
public class MessageController {
 private final MessageService s;public MessageController(MessageService s){this.s=s;}
 @GetMapping public ApiResponse<?> list(@AuthenticationPrincipal AuthPrincipal p,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="false")boolean unread,@RequestParam(defaultValue="")String keyword){return ApiResponse.ok(s.list(p,page,size,unread,keyword));}
 @GetMapping("/unread-count") public ApiResponse<?> count(@AuthenticationPrincipal AuthPrincipal p){return ApiResponse.ok(s.count(p));}
 @PatchMapping("/{id}/read") public ApiResponse<?> read(@AuthenticationPrincipal AuthPrincipal p,@PathVariable long id){return ApiResponse.ok(s.read(p,id));}
 @PostMapping("/read-all") public ApiResponse<?> all(@AuthenticationPrincipal AuthPrincipal p){return ApiResponse.ok(s.readAll(p));}
}
