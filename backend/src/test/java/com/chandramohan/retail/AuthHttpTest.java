package com.chandramohan.retail;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthHttpTest {
  @LocalServerPort int port;
  final HttpClient client = HttpClient.newHttpClient();

  HttpResponse<String> call(String method, String path, String body, String token)
      throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
            .header("Content-Type", "application/json");
    if (token != null) b.header("Authorization", "Bearer " + token);
    return client.send(
        b.method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void protectedApiRequiresToken() throws Exception {
    assertEquals(401, call("GET", "/auth/me", null, null).statusCode());
  }

  @Test
  void invalidRegistrationRejected() throws Exception {
    assertEquals(
        400,
        call("POST", "/auth/register", "{\"email\":\"bad\",\"password\":\"short\"}", null)
            .statusCode());
  }

  @Test
  void customerRegistrationLoginAndLogout() throws Exception {
    String body =
        "{\"email\":\"" + UUID.randomUUID() + "@example.test\",\"password\":\"Test-password-123\"}";
    var r = call("POST", "/auth/register", body, null);
    assertEquals(201, r.statusCode(), r.body());
    var m = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(r.body());
    assertTrue(m.find(), r.body());
    String token = m.group(1);
    assertTrue(call("GET", "/auth/me", null, token).body().contains("CUSTOMER"));
    assertEquals(409, call("POST", "/auth/register", body, null).statusCode());
    assertEquals(200, call("POST", "/auth/login", body, null).statusCode());
    assertEquals(204, call("POST", "/auth/logout", null, token).statusCode());
    assertEquals(401, call("GET", "/auth/me", null, token).statusCode());
  }
}
