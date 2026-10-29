package com.chandramohan.retail;

import static org.junit.jupiter.api.Assertions.*;

import com.chandramohan.retail.auth.*;
import com.chandramohan.retail.domain.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    properties = {"ADMIN_EMAIL=admin@example.test", "ADMIN_PASSWORD=Admin-test-password-123"})
class DomainTest {
  @Autowired LabStore users;
  @Autowired JdbcTemplate db;

  String user() {
    return users
        .register(UUID.randomUUID() + "@example.test", "test-unused-hash")
        .orElseThrow()
        .id()
        .toString();
  }

  String admin() {
    return users.find("admin@example.test").orElseThrow().id().toString();
  }

  void eq(String a, BigDecimal b) {
    assertEquals(0, new BigDecimal(a).compareTo(b));
  }

  @Autowired GroceryService s;

  @Test
  void checkoutReservesAndConfirmsOnlyOnce() {
    String u = user();
    s.cart(u, "rice", 2);
    int stock = s.products("Brown Rice", "").get(0).stock();
    var o = s.reserve(u, "checkout-key-1");
    assertEquals(o.id(), s.reserve(u, "checkout-key-1").id());
    eq("5", o.total());
    assertEquals(stock - 2, s.products("Brown Rice", "").get(0).stock());
    assertEquals("CONFIRMED", s.checkout(u, o.id(), true).status());
    assertEquals("CONFIRMED", s.checkout(u, o.id(), true).status());
    assertEquals(1, s.orders(u).size());
  }

  @Test
  void cancelAndExpireReleaseStockExactlyOnce() {
    String u = user();
    s.cart(u, "rice-alt", 1);
    int stock = s.products("White Rice", "").get(0).stock();
    var o = s.reserve(u, "cancel-key-1");
    s.checkout(u, o.id(), false);
    s.checkout(u, o.id(), false);
    assertEquals(stock, s.products("White Rice", "").get(0).stock());
    s.cart(u, "rice-alt", 1);
    var exp = s.reserve(u, "expire-key-1");
    db.update(
        "UPDATE orders SET expires_at=? WHERE id=?",
        Instant.now().minusSeconds(10).toString(),
        exp.id());
    assertEquals("EXPIRED", s.checkout(u, exp.id(), true).status());
    assertEquals(stock, s.products("White Rice", "").get(0).stock());
  }

  @Test
  void preferenceAndSubstitutionAreValidated() {
    String u = user();
    s.preference(u, "VEGAN");
    assertThrows(Exception.class, () -> s.cart(u, "chicken", 1));
    s.cart(u, "rice", 1);
    s.substitute(u, "rice", "rice-alt");
    assertEquals("rice-alt", s.cart(u).get(0).productId());
    assertThrows(Exception.class, () -> s.substitute(u, "rice-alt", "oats"));
    assertTrue(s.recipes(u).stream().allMatch(r -> r.diet().equals("VEGAN")));
  }

  @Test
  void ingredientImportAggregatesAndIsIdempotent() {
    String u = user();
    String day = "2026-11-02";
    s.plan(u, day, "LUNCH", "chickpea-rice", 1);
    s.plan(u, day, "DINNER", "chickpea-rice", 2);
    s.ingredientsToCart(u, day, "meal-key-01");
    assertEquals(9, s.cart(u).stream().mapToInt(GroceryService.Item::quantity).sum());
    s.ingredientsToCart(u, day, "meal-key-01");
    assertEquals(9, s.cart(u).stream().mapToInt(GroceryService.Item::quantity).sum());
    s.removePlan(u, day, "DINNER");
    assertThrows(Exception.class, () -> s.ingredientsToCart(u, day, "meal-key-01"));
  }

  @Test
  void unavailableIngredientRollsBackEntireImport() {
    String u = user();
    s.plan(u, "2026-11-03", "LUNCH", "porridge", 1);
    db.update("UPDATE products SET stock=0 WHERE ingredient='milk'");
    try {
      assertThrows(Exception.class, () -> s.ingredientsToCart(u, "2026-11-03", "meal-key-02"));
      assertTrue(s.cart(u).isEmpty());
    } finally {
      db.update("UPDATE products SET stock=20 WHERE ingredient='milk'");
    }
  }

  @Test
  void customerCannotAdministerOrReadAnotherOrder() {
    String u = user(), v = user();
    assertThrows(Exception.class, () -> s.stock(u, "rice", 50));
    s.cart(u, "oats", 1);
    var o = s.reserve(u, "private-key-1");
    assertThrows(Exception.class, () -> s.checkout(v, o.id(), true));
    assertTrue(s.orders(v).isEmpty());
  }

  @Test
  void concurrentCheckoutDoesNotOversell() throws Exception {
    db.update("UPDATE products SET stock=1 WHERE id='chickpea-alt'");
    String a = user(), b = user();
    s.cart(a, "chickpea-alt", 1);
    s.cart(b, "chickpea-alt", 1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var rs =
          pool.invokeAll(
              List.<Callable<Boolean>>of(
                  () -> {
                    try {
                      s.reserve(a, "concurrent-a");
                      return true;
                    } catch (Exception e) {
                      return false;
                    }
                  },
                  () -> {
                    try {
                      s.reserve(b, "concurrent-b");
                      return true;
                    } catch (Exception e) {
                      return false;
                    }
                  }));
      int count = 0;
      for (var r : rs) if (r.get()) count++;
      assertEquals(1, count);
      assertEquals(0, s.products("Organic Chickpeas", "").get(0).stock());
    } finally {
      pool.shutdownNow();
      db.update("UPDATE products SET stock=40 WHERE id='chickpea-alt'");
    }
  }
}
