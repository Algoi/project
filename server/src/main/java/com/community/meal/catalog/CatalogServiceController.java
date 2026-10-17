package com.community.meal.catalog;

import com.community.meal.auth.Access;
import com.community.meal.auth.AuthPrincipal;
import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.time.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogServiceController {
  private final CatalogService service;
  private final Access access;

  public CatalogServiceController(CatalogService service, Access access) {
    this.service = service;
    this.access = access;
  }

  @GetMapping("/dish-categories")
  public ApiResponse<?> categories(@AuthenticationPrincipal AuthPrincipal p) {
    return ApiResponse.ok(service.dictionary(p, "dish_category"));
  }

  @PostMapping("/dish-categories")
  public ApiResponse<?> categoryCreate(
      @AuthenticationPrincipal AuthPrincipal p, @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(service.saveDictionary(p, "dish_category", null, input));
  }

  @PutMapping("/dish-categories/{id}")
  public ApiResponse<?> categoryUpdate(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(service.saveDictionary(p, "dish_category", id, input));
  }

  @GetMapping("/allergens")
  public ApiResponse<?> allergens(@AuthenticationPrincipal AuthPrincipal p) {
    return ApiResponse.ok(service.dictionary(p, "allergen"));
  }

  @PostMapping("/allergens")
  public ApiResponse<?> allergenCreate(
      @AuthenticationPrincipal AuthPrincipal p, @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(service.saveDictionary(p, "allergen", null, input));
  }

  @PutMapping("/allergens/{id}")
  public ApiResponse<?> allergenUpdate(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(service.saveDictionary(p, "allergen", id, input));
  }

  @DeleteMapping("/allergens/{id}")
  public ApiResponse<?> allergenDelete(
      @AuthenticationPrincipal AuthPrincipal p, @PathVariable long id) {
    service.deleteDictionary(p, "allergen", id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/dishes")
  public ApiResponse<?> dishes(
      @AuthenticationPrincipal AuthPrincipal p,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "") String keyword) {
    return ApiResponse.ok(service.dishes(p, page, size, keyword));
  }

  @GetMapping("/dishes/{id}")
  public ApiResponse<?> dish(@AuthenticationPrincipal AuthPrincipal p, @PathVariable long id) {
    return ApiResponse.ok(service.dish(p, id));
  }

  @PostMapping("/dishes")
  public ApiResponse<?> dishCreate(
      @AuthenticationPrincipal AuthPrincipal p, @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(service.saveDish(p, null, input));
  }

  @PutMapping("/dishes/{id}")
  public ApiResponse<?> dishUpdate(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(service.saveDish(p, id, input));
  }

  @PatchMapping("/dishes/{id}/status")
  public ApiResponse<?> dishStatus(
      @AuthenticationPrincipal AuthPrincipal p,
      @PathVariable long id,
      @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(service.statusDish(p, id, input));
  }
}
