package com.chandramohan.retail.domain;

import java.math.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

public final class Rules {
  private Rules() {}

  public static String id() {
    return UUID.randomUUID().toString();
  }

  public static String now() {
    return java.time.Instant.now().toString();
  }

  public static void check(boolean ok, String message) {
    if (!ok) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  public static void conflict(boolean ok, String message) {
    if (!ok) throw new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  public static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
  }

  public static void admin(JdbcTemplate db, String user) {
    if (!"ADMIN"
        .equals(db.queryForObject("SELECT app_role FROM app_users WHERE id=?", String.class, user)))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access required");
  }

  public static void lock(JdbcTemplate db, String user) {
    db.queryForObject("SELECT id FROM app_users WHERE id=? FOR UPDATE", String.class, user);
  }

  public static void key(String key) {
    check(
        key != null && key.matches("[A-Za-z0-9_-]{8,80}"),
        "Use a request key of 8-80 letters, digits, underscore or hyphen");
  }

  public static BigDecimal money(BigDecimal n) {
    return n.setScale(2, RoundingMode.HALF_UP);
  }
}
