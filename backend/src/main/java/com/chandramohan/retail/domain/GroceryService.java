package com.chandramohan.retail.domain;

import static com.chandramohan.retail.domain.Rules.*;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroceryService {
  public record Product(
      String id,
      String name,
      String category,
      String ingredient,
      String diet,
      String unit,
      BigDecimal price,
      int stock) {}

  public record Item(
      String productId,
      String name,
      int quantity,
      BigDecimal price,
      int stock,
      BigDecimal subtotal) {}

  public record Recipe(
      String id,
      String name,
      String diet,
      String instructions,
      List<Map<String, Object>> ingredients) {}

  public record Plan(String date, String slot, String recipeId, String name, int servings) {}

  public record Order(
      String id,
      String status,
      BigDecimal total,
      String expiresAt,
      String createdAt,
      List<Map<String, Object>> lines) {}

  private final JdbcTemplate db;

  public GroceryService(JdbcTemplate db) {
    this.db = db;
  }

  public List<Product> products(String q, String category) {
    return db.query(
        "SELECT * FROM products WHERE LOWER(name) LIKE ? AND (?='' OR category=?) ORDER BY"
            + " category,name",
        (r, n) ->
            new Product(
                r.getString("id"),
                r.getString("name"),
                r.getString("category"),
                r.getString("ingredient"),
                r.getString("diet"),
                r.getString("unit"),
                r.getBigDecimal("price"),
                r.getInt("stock")),
        "%" + q.toLowerCase(Locale.ROOT) + "%",
        category,
        category);
  }

  private Product product(String id) {
    return products("", "").stream()
        .filter(p -> p.id().equals(id))
        .findFirst()
        .orElseThrow(Rules::missing);
  }

  public String preference(String owner) {
    return db
        .queryForList("SELECT diet FROM preferences WHERE owner_id=?", String.class, owner)
        .stream()
        .findFirst()
        .orElse("ANY");
  }

  private boolean allowed(String wanted, String actual) {
    return wanted.equals("ANY")
        || wanted.equals(actual)
        || wanted.equals("VEGETARIAN") && actual.equals("VEGAN");
  }

  @Transactional
  public void preference(String owner, String diet) {
    check(diet != null && Set.of("ANY", "VEGETARIAN", "VEGAN").contains(diet), "Unknown diet");
    lock(db, owner);
    if (db.update("UPDATE preferences SET diet=? WHERE owner_id=?", diet, owner) == 0)
      db.update("INSERT INTO preferences VALUES(?,?)", owner, diet);
  }

  public List<Item> cart(String owner) {
    return db.query(
        "SELECT p.*,c.quantity FROM carts c JOIN products p ON p.id=c.product_id WHERE c.owner_id=?"
            + " ORDER BY p.name",
        (r, n) ->
            new Item(
                r.getString("id"),
                r.getString("name"),
                r.getInt("quantity"),
                r.getBigDecimal("price"),
                r.getInt("stock"),
                money(r.getBigDecimal("price").multiply(BigDecimal.valueOf(r.getInt("quantity"))))),
        owner);
  }

  @Transactional
  public void cart(String owner, String id, int quantity) {
    check(quantity >= 0 && quantity <= 100, "Quantity must be 0-100");
    lock(db, owner);
    if (quantity == 0) {
      db.update("DELETE FROM carts WHERE owner_id=? AND product_id=?", owner, id);
      return;
    }
    var p = product(id);
    check(allowed(preference(owner), p.diet()), "Product conflicts with your dietary preference");
    if (db.update(
            "UPDATE carts SET quantity=? WHERE owner_id=? AND product_id=?", quantity, owner, id)
        == 0) db.update("INSERT INTO carts VALUES(?,?,?)", owner, id, quantity);
  }

  public List<Product> substitutes(String owner, String id) {
    var p = product(id);
    return products("", p.category()).stream()
        .filter(
            x ->
                !x.id().equals(id)
                    && x.ingredient().equals(p.ingredient())
                    && x.unit().equals(p.unit())
                    && x.stock() > 0
                    && allowed(preference(owner), x.diet()))
        .toList();
  }

  @Transactional
  public void substitute(String owner, String from, String to) {
    lock(db, owner);
    var item =
        cart(owner).stream()
            .filter(i -> i.productId().equals(from))
            .findFirst()
            .orElseThrow(Rules::missing);
    check(
        substitutes(owner, from).stream().anyMatch(p -> p.id().equals(to)),
        "Select a compatible available substitute");
    int existing =
        cart(owner).stream().filter(i -> i.productId().equals(to)).mapToInt(Item::quantity).sum();
    cart(owner, to, existing + item.quantity());
    cart(owner, from, 0);
  }

  public List<Recipe> recipes(String owner) {
    return db
        .query(
            "SELECT * FROM recipes ORDER BY name",
            (r, n) ->
                new Recipe(
                    r.getString("id"),
                    r.getString("name"),
                    r.getString("diet"),
                    r.getString("instructions"),
                    db.queryForList(
                        "SELECT ingredient,units_per_serving FROM ingredients WHERE recipe_id=?"
                            + " ORDER BY ingredient",
                        r.getString("id"))))
        .stream()
        .filter(r -> allowed(preference(owner), r.diet()))
        .toList();
  }

  public List<Plan> plan(String owner, String week) {
    LocalDate start = LocalDate.parse(week);
    return db.query(
        "SELECT p.*,r.name FROM meal_plan p JOIN recipes r ON r.id=p.recipe_id WHERE owner_id=? AND"
            + " plan_date>=? AND plan_date<=? ORDER BY plan_date,slot",
        (r, n) ->
            new Plan(
                r.getString("plan_date"),
                r.getString("slot"),
                r.getString("recipe_id"),
                r.getString("name"),
                r.getInt("servings")),
        owner,
        start.toString(),
        start.plusDays(6).toString());
  }

  @Transactional
  public void plan(String owner, String date, String slot, String recipe, int servings) {
    LocalDate.parse(date);
    check(Set.of("BREAKFAST", "LUNCH", "DINNER").contains(slot), "Unknown meal slot");
    check(servings >= 1 && servings <= 10, "Servings must be 1-10");
    lock(db, owner);
    check(
        recipes(owner).stream().anyMatch(r -> r.id().equals(recipe)),
        "Recipe conflicts with preference or does not exist");
    db.update(
        "DELETE FROM meal_plan WHERE owner_id=? AND plan_date=? AND slot=?", owner, date, slot);
    db.update("INSERT INTO meal_plan VALUES(?,?,?,?,?)", owner, date, slot, recipe, servings);
  }

  @Transactional
  public void removePlan(String owner, String date, String slot) {
    lock(db, owner);
    db.update(
        "DELETE FROM meal_plan WHERE owner_id=? AND plan_date=? AND slot=?", owner, date, slot);
  }

  @Transactional
  public void ingredientsToCart(String owner, String week, String key) {
    key(key);
    lock(db, owner);
    var plans = plan(owner, week);
    check(!plans.isEmpty(), "Plan at least one meal");
    String fingerprint;
    try {
      fingerprint =
          java.util.HexFormat.of()
              .formatHex(
                  java.security.MessageDigest.getInstance("SHA-256")
                      .digest(
                          (week + ":" + plans).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    var prior =
        db.queryForList(
            "SELECT fingerprint FROM meal_imports WHERE owner_id=? AND request_key=?",
            String.class,
            owner,
            key);
    if (!prior.isEmpty()) {
      conflict(prior.get(0).equals(fingerprint), "Key reused with a different plan");
      return;
    }
    Map<String, Integer> quantities = new TreeMap<>();
    for (var m : plans) {
      check(
          recipes(owner).stream().anyMatch(r -> r.id().equals(m.recipeId())),
          "Meal conflicts with preference");
      db.query(
          "SELECT * FROM ingredients WHERE recipe_id=?",
          r -> {
            quantities.merge(
                r.getString("ingredient"),
                r.getInt("units_per_serving") * m.servings(),
                Integer::sum);
          },
          m.recipeId());
    }
    for (var e : quantities.entrySet()) {
      var choice =
          products("", "").stream()
              .filter(
                  x ->
                      x.ingredient().equals(e.getKey())
                          && x.stock() >= e.getValue()
                          && allowed(preference(owner), x.diet()))
              .min(Comparator.comparing(Product::price))
              .orElseThrow(
                  () ->
                      new org.springframework.web.server.ResponseStatusException(
                          org.springframework.http.HttpStatus.CONFLICT,
                          "No available ingredient: " + e.getKey()));
      int old =
          cart(owner).stream()
              .filter(i -> i.productId().equals(choice.id()))
              .mapToInt(Item::quantity)
              .sum();
      cart(owner, choice.id(), old + e.getValue());
    }
    db.update("INSERT INTO meal_imports VALUES(?,?,?)", owner, key, fingerprint);
  }

  private Order order(String owner, String id, boolean forUpdate) {
    return db
        .query(
            "SELECT * FROM orders WHERE owner_id=? AND id=?" + (forUpdate ? " FOR UPDATE" : ""),
            (r, n) ->
                new Order(
                    r.getString("id"),
                    r.getString("status"),
                    r.getBigDecimal("total"),
                    r.getString("expires_at"),
                    r.getString("created_at"),
                    db.queryForList(
                        "SELECT product_id,name,quantity,price FROM order_lines WHERE order_id=?"
                            + " ORDER BY product_id",
                        id)),
            owner,
            id)
        .stream()
        .findFirst()
        .orElseThrow(Rules::missing);
  }

  public List<Order> orders(String owner) {
    return db
        .queryForList(
            "SELECT id FROM orders WHERE owner_id=? ORDER BY created_at DESC", String.class, owner)
        .stream()
        .map(id -> order(owner, id, false))
        .toList();
  }

  private void release(String id) {
    var rows =
        db.queryForList(
            "SELECT product_id,quantity FROM order_lines WHERE order_id=? ORDER BY product_id", id);
    for (var row : rows)
      db.update(
          "UPDATE products SET stock=stock+? WHERE id=?",
          ((Number) row.get("quantity")).intValue(),
          row.get("product_id"));
  }

  @Transactional
  public Order reserve(String owner, String key) {
    key(key);
    lock(db, owner);
    var old =
        db.queryForList(
            "SELECT id FROM orders WHERE owner_id=? AND request_key=?", String.class, owner, key);
    if (!old.isEmpty()) return order(owner, old.get(0), false);
    var items = new ArrayList<>(cart(owner));
    check(!items.isEmpty(), "Cart is empty");
    items.sort(Comparator.comparing(Item::productId));
    BigDecimal total = BigDecimal.ZERO;
    for (var i : items) {
      db.queryForObject(
          "SELECT id FROM products WHERE id=? FOR UPDATE", String.class, i.productId());
      var p = product(i.productId());
      conflict(p.stock() >= i.quantity(), "Insufficient stock for " + p.name());
      check(allowed(preference(owner), p.diet()), "Product conflicts with preference");
      total = total.add(p.price().multiply(BigDecimal.valueOf(i.quantity())));
    }
    String id = id(), time = now();
    db.update(
        "INSERT INTO orders VALUES(?,?,'RESERVED',?,?,?,?)",
        id,
        owner,
        money(total),
        Instant.now().plusSeconds(600).toString(),
        key,
        time);
    for (var i : items) {
      var p = product(i.productId());
      db.update("UPDATE products SET stock=stock-? WHERE id=?", i.quantity(), p.id());
      db.update(
          "INSERT INTO order_lines VALUES(?,?,?,?,?)",
          id,
          p.id(),
          p.name(),
          i.quantity(),
          p.price());
    }
    db.update("DELETE FROM carts WHERE owner_id=?", owner);
    return order(owner, id, false);
  }

  @Transactional
  public Order checkout(String owner, String id, boolean confirm) {
    lock(db, owner);
    var o = order(owner, id, true);
    if (o.status().equals("CONFIRMED") && confirm) return o;
    if (!o.status().equals("RESERVED")) return o;
    if (Instant.parse(o.expiresAt()).isBefore(Instant.now())) {
      release(id);
      db.update("UPDATE orders SET status='EXPIRED' WHERE id=?", id);
    } else if (confirm) db.update("UPDATE orders SET status='CONFIRMED' WHERE id=?", id);
    else {
      release(id);
      db.update("UPDATE orders SET status='CANCELLED' WHERE id=?", id);
    }
    return order(owner, id, false);
  }

  @Transactional
  public void expire() {
    for (var row :
        db.queryForList(
            "SELECT id,owner_id FROM orders WHERE status='RESERVED' AND expires_at<? ORDER BY id",
            now())) checkout((String) row.get("owner_id"), (String) row.get("id"), false);
  }

  @Transactional
  public void stock(String owner, String product, int stock) {
    admin(db, owner);
    check(stock >= 0 && stock <= 100000, "Stock must be 0-100000");
    conflict(
        db.update("UPDATE products SET stock=? WHERE id=?", stock, product) == 1,
        "Unknown product");
  }
}
