package com.community.meal.menu;

import com.community.meal.auth.AuthPrincipal;
import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.time.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sites/{siteId}")
public class MenuController {
  private final MenuService s;

  public MenuController(MenuService s) {
    this.s = s;
  }

  @GetMapping("/menus")
  public ApiResponse<?> get(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long siteId,
      @RequestParam String date,
      @RequestParam(defaultValue = "LUNCH") String mealType) {
    return ApiResponse.ok(s.get(p, siteId, Values.date(date), Values.meal(mealType)));
  }

  @PostMapping("/menus")
  public ApiResponse<?> create(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long siteId,
      @RequestBody Map<String, Object> in) {
    return ApiResponse.ok(s.save(p, siteId, null, in));
  }

  @PutMapping("/menus/{id}")
  public ApiResponse<?> update(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long siteId,
      @PathVariable long id,
      @RequestBody Map<String, Object> in) {
    return ApiResponse.ok(s.save(p, siteId, id, in));
  }

  @PostMapping("/menus/{id}/{action:submit|publish}")
  public ApiResponse<?> transition(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long siteId,
      @PathVariable long id,
      @PathVariable String action) {
    return ApiResponse.ok(s.transition(p, siteId, id, action.equals("publish")));
  }

  @PatchMapping("/menu-items/{id}/availability")
  public ApiResponse<?> availability(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long siteId,
      @PathVariable long id,
      @RequestBody Map<String, Object> in) {
    return ApiResponse.ok(s.availability(p, siteId, id, in));
  }
  @PostMapping("/menus/{id}/copy") public ApiResponse<?> copy(@AuthenticationPrincipal AuthPrincipal p,@PathVariable long siteId,@PathVariable long id,@RequestBody Map<String,Object> in){return ApiResponse.ok(s.copy(p,siteId,id,in));}
  @GetMapping("/menu-templates") public ApiResponse<?> templates(@AuthenticationPrincipal AuthPrincipal p,@PathVariable long siteId,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.ok(s.templates(p,siteId,page,size));}
  @PostMapping("/menu-templates") public ApiResponse<?> saveTemplate(@AuthenticationPrincipal AuthPrincipal p,@PathVariable long siteId,@RequestBody Map<String,Object> in){return ApiResponse.ok(s.saveTemplate(p,siteId,in));}
  @PostMapping("/menu-templates/{id}/apply") public ApiResponse<?> applyTemplate(@AuthenticationPrincipal AuthPrincipal p,@PathVariable long siteId,@PathVariable long id,@RequestBody Map<String,Object> in){return ApiResponse.ok(s.applyTemplate(p,siteId,id,in));}
  @DeleteMapping("/menu-templates/{id}") public ApiResponse<?> deleteTemplate(@AuthenticationPrincipal AuthPrincipal p,@PathVariable long siteId,@PathVariable long id){return ApiResponse.ok(s.deleteTemplate(p,siteId,id));}

}
