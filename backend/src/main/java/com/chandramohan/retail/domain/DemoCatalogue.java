package com.chandramohan.retail.domain;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoCatalogue implements CommandLineRunner {
  private final JdbcTemplate db;

  public DemoCatalogue(JdbcTemplate db) {
    this.db = db;
  }

  @Transactional
  public void run(String... args) {
    if (db.queryForObject("SELECT COUNT(*) FROM products", Integer.class) > 0) return;
    String[][] products = {
      {"rice", "Brown Rice", "PANTRY", "rice", "VEGAN", "pack", "2.50", "50"},
      {"rice-alt", "White Rice", "PANTRY", "rice", "VEGAN", "pack", "2.00", "50"},
      {"chickpea", "Chickpeas", "PANTRY", "chickpea", "VEGAN", "can", "1.50", "40"},
      {"chickpea-alt", "Organic Chickpeas", "PANTRY", "chickpea", "VEGAN", "can", "2.10", "40"},
      {"tomato", "Tomatoes", "PRODUCE", "tomato", "VEGAN", "pack", "2.00", "30"},
      {"tomato-alt", "Cherry Tomatoes", "PRODUCE", "tomato", "VEGAN", "pack", "2.60", "30"},
      {"milk", "Milk", "DAIRY", "milk", "VEGETARIAN", "carton", "1.60", "20"},
      {"oatmilk", "Oat Milk", "DAIRY", "milk", "VEGAN", "carton", "2.30", "20"},
      {"oats", "Rolled Oats", "PANTRY", "oats", "VEGAN", "pack", "1.90", "40"},
      {"chicken", "Chicken Breast", "PROTEIN", "chicken", "ANY", "pack", "5.50", "20"}
    };
    for (var p : products)
      db.update(
          "INSERT INTO products VALUES(?,?,?,?,?,?,?,?)",
          p[0],
          p[1],
          p[2],
          p[3],
          p[4],
          p[5],
          new java.math.BigDecimal(p[6]),
          Integer.parseInt(p[7]));
    db.update(
        "INSERT INTO recipes VALUES('chickpea-rice','Chickpea rice bowl','VEGAN','Cook rice. Simmer"
            + " chickpeas and tomatoes; combine.')");
    db.update(
        "INSERT INTO recipes VALUES('porridge','Breakfast porridge','VEGETARIAN','Cook oats with"
            + " milk until soft.')");
    db.update(
        "INSERT INTO recipes VALUES('chicken-rice','Chicken rice bowl','ANY','Cook chicken"
            + " thoroughly and serve with cooked rice.')");
    for (var r :
        new String[][] {
          {"chickpea-rice", "rice"},
          {"chickpea-rice", "chickpea"},
          {"chickpea-rice", "tomato"},
          {"porridge", "oats"},
          {"porridge", "milk"},
          {"chicken-rice", "chicken"},
          {"chicken-rice", "rice"}
        }) db.update("INSERT INTO ingredients VALUES(?,?,1)", r[0], r[1]);
  }
}
