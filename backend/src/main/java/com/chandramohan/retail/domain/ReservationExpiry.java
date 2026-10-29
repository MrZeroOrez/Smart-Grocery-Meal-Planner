package com.chandramohan.retail.domain;

import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
public class ReservationExpiry {
  private final GroceryService s;
  private final JdbcTemplate db;

  public ReservationExpiry(GroceryService s, JdbcTemplate db) {
    this.s = s;
    this.db = db;
  }

  @Scheduled(fixedDelay = 30000)
  public void expire() {
    for (var row :
        db.queryForList(
            "SELECT id,owner_id FROM orders WHERE status='RESERVED' AND expires_at<? ORDER BY id",
            Rules.now())) s.checkout((String) row.get("owner_id"), (String) row.get("id"), false);
  }
}
