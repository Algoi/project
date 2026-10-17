package com.community.meal.elder;

import com.community.meal.auth.AuthPrincipal;
import com.community.meal.common.ApiResponse;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ElderController {
  private final ElderService s;

  public ElderController(ElderService s) {
    this.s = s;
  }

  @GetMapping("/service-sites")
  public ApiResponse<?> serviceSites() {
    return ApiResponse.ok(s.serviceSites());
  }

  @GetMapping("/elders")
  public ApiResponse<?> list(
      @AuthenticationPrincipal AuthPrincipal p,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "") String keyword) {
    return ApiResponse.ok(s.list(p, page, size, keyword));
  }

  @GetMapping("/elders/{id}")
  public ApiResponse<?> get(@AuthenticationPrincipal AuthPrincipal p, @PathVariable long id) {
    return ApiResponse.ok(s.get(p, id));
  }

  @PostMapping("/elders")
  public ApiResponse<?> create(
      @AuthenticationPrincipal AuthPrincipal p, @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(s.save(p, null, input));
  }

  @PutMapping("/elders/{id}")
  public ApiResponse<?> update(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(s.save(p, id, input));
  }

  @GetMapping("/elders/{id}/health")
  public ApiResponse<?> health(@AuthenticationPrincipal AuthPrincipal p, @PathVariable long id) {
    return ApiResponse.ok(s.health(p, id));
  }

  @PutMapping("/elders/{id}/health")
  public ApiResponse<?> healthSave(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(s.saveHealth(p, id, input));
  }

  @PostMapping("/family/bindings")
  public ApiResponse<?> binding(
      @AuthenticationPrincipal AuthPrincipal p, @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(s.requestBinding(p, input));
  }

  @GetMapping("/family/bindings")
  public ApiResponse<?> bindings(@AuthenticationPrincipal AuthPrincipal p) {
    return ApiResponse.ok(s.bindings(p, false));
  }

  @GetMapping("/bindings/pending")
  public ApiResponse<?> pending(@AuthenticationPrincipal AuthPrincipal p) {
    return ApiResponse.ok(s.bindings(p, true));
  }

  @PostMapping("/bindings/{id}/{decision:approve|reject}")
  public ApiResponse<?> reviewBinding(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @PathVariable String decision,
      @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(s.reviewBinding(p, id, decision.equals("approve"), input));
  }

  @PostMapping("/subsidy-applications")
  public ApiResponse<?> apply(
      @AuthenticationPrincipal AuthPrincipal p, @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(s.applySubsidy(p, input));
  }

  @GetMapping("/subsidy-applications")
  public ApiResponse<?> subsidies(
      @AuthenticationPrincipal AuthPrincipal p,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="")String status,@RequestParam(required=false)Long siteId) {
    return ApiResponse.ok(s.subsidies(p, page, size, keyword,status,siteId));
  }

  @GetMapping("/subsidy-applications/{id}")
  public ApiResponse<?> subsidy(@AuthenticationPrincipal AuthPrincipal p, @PathVariable long id) {
    return ApiResponse.ok(s.subsidy(p, id));
  }

  @PostMapping("/subsidy-applications/{id}/{decision:approve|reject}")
  public ApiResponse<?> reviewSubsidy(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @PathVariable String decision,
      @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(s.reviewSubsidy(p, id, decision.equals("approve"), input));
  }
  @PostMapping("/bindings/{id}/revoke") public ApiResponse<?> revoke(@AuthenticationPrincipal AuthPrincipal p,@PathVariable long id,@RequestBody Map<String,Object> input){return ApiResponse.ok(s.revokeBinding(p,id,input));}
  @GetMapping("/bindings") public ApiResponse<?> managedBindings(@AuthenticationPrincipal AuthPrincipal p,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="")String keyword,@RequestParam(required=false)Long siteId){return ApiResponse.ok(s.managedBindings(p,page,size,status,keyword,siteId));}
  @GetMapping("/bindings/history") public ApiResponse<?> history(@AuthenticationPrincipal AuthPrincipal p,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.ok(s.bindingHistory(p,page,size));}
  @PostMapping("/elders/{id}/subsidy/terminate") public ApiResponse<?> terminate(@AuthenticationPrincipal AuthPrincipal p,@PathVariable long id,@RequestBody Map<String,Object> input){return ApiResponse.ok(s.terminateSubsidy(p,id,input));}
  @GetMapping("/elders/{id}/subsidy-history") public ApiResponse<?> subsidyHistory(@AuthenticationPrincipal AuthPrincipal p,@PathVariable long id){return ApiResponse.ok(s.subsidyHistory(p,id));}

}
