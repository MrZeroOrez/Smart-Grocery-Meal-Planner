package com.chandramohan.retail.domain;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class GroceryController {
  final GroceryService s;

  public GroceryController(GroceryService s) {
    this.s = s;
  }

  public record Quantity(int quantity) {}

  public record Diet(String diet) {}

  public record Meal(String date, String slot, String recipeId, int servings) {}

  public record Week(String week) {}

  public record Stock(int stock) {}

  @GetMapping("/products")
  public Object products(
      @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "") String category) {
    return s.products(q, category);
  }

  @GetMapping("/preferences")
  public Object preference(@AuthenticationPrincipal Jwt j) {
    return java.util.Map.of("diet", s.preference(j.getSubject()));
  }

  @PutMapping("/preferences")
  public void preference(@AuthenticationPrincipal Jwt j, @RequestBody Diet b) {
    s.preference(j.getSubject(), b.diet());
  }

  @GetMapping("/cart")
  public Object cart(@AuthenticationPrincipal Jwt j) {
    return s.cart(j.getSubject());
  }

  @PutMapping("/cart/{id}")
  public void cart(
      @AuthenticationPrincipal Jwt j, @PathVariable String id, @RequestBody Quantity b) {
    s.cart(j.getSubject(), id, b.quantity());
  }

  @GetMapping("/products/{id}/substitutes")
  public Object alternatives(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
    return s.substitutes(j.getSubject(), id);
  }

  @PostMapping("/cart/{from}/substitute/{to}")
  public void substitute(
      @AuthenticationPrincipal Jwt j, @PathVariable String from, @PathVariable String to) {
    s.substitute(j.getSubject(), from, to);
  }

  @GetMapping("/recipes")
  public Object recipes(@AuthenticationPrincipal Jwt j) {
    return s.recipes(j.getSubject());
  }

  @GetMapping("/plan")
  public Object plan(@AuthenticationPrincipal Jwt j, @RequestParam String week) {
    return s.plan(j.getSubject(), week);
  }

  @PutMapping("/plan")
  public void plan(@AuthenticationPrincipal Jwt j, @RequestBody Meal b) {
    s.plan(j.getSubject(), b.date(), b.slot(), b.recipeId(), b.servings());
  }

  @DeleteMapping("/plan")
  public void delete(
      @AuthenticationPrincipal Jwt j, @RequestParam String date, @RequestParam String slot) {
    s.removePlan(j.getSubject(), date, slot);
  }

  @PostMapping("/plan/cart")
  public void ingredients(
      @AuthenticationPrincipal Jwt j,
      @RequestBody Week b,
      @RequestHeader("Idempotency-Key") String key) {
    s.ingredientsToCart(j.getSubject(), b.week(), key);
  }

  @PostMapping("/reservations")
  public Object reserve(
      @AuthenticationPrincipal Jwt j, @RequestHeader("Idempotency-Key") String key) {
    return s.reserve(j.getSubject(), key);
  }

  @PostMapping("/orders/{id}/confirm")
  public Object confirm(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
    return s.checkout(j.getSubject(), id, true);
  }

  @PostMapping("/orders/{id}/cancel")
  public Object cancel(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
    return s.checkout(j.getSubject(), id, false);
  }

  @GetMapping("/orders")
  public Object orders(@AuthenticationPrincipal Jwt j) {
    return s.orders(j.getSubject());
  }

  @PutMapping("/admin/products/{id}/stock")
  public void stock(@AuthenticationPrincipal Jwt j, @PathVariable String id, @RequestBody Stock b) {
    s.stock(j.getSubject(), id, b.stock());
  }
}
