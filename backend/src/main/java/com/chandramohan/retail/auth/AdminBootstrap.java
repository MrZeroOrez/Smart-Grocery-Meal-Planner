package com.chandramohan.retail.auth;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements CommandLineRunner {
  private final Environment env;
  private final LabStore users;
  private final JdbcTemplate db;
  private final PasswordEncoder encoder;

  public AdminBootstrap(Environment env, LabStore users, JdbcTemplate db, PasswordEncoder encoder) {
    this.env = env;
    this.users = users;
    this.db = db;
    this.encoder = encoder;
  }

  public void run(String... args) {
    String email = env.getProperty("ADMIN_EMAIL"), password = env.getProperty("ADMIN_PASSWORD");
    if (email == null && password == null) return;
    if (email == null
        || password == null
        || !email.contains("@")
        || password.length() < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException(
          "Set valid ADMIN_EMAIL and ADMIN_PASSWORD (12-72 UTF-8 bytes)");
    var u = users.find(email);
    if (u.isPresent()) {
      if (!encoder.matches(password, u.get().passwordHash()))
        throw new IllegalStateException("Existing admin email password mismatch");
    } else u = users.register(email, encoder.encode(password));
    db.update("UPDATE app_users SET app_role='ADMIN' WHERE id=?", u.orElseThrow().id().toString());
  }
}
