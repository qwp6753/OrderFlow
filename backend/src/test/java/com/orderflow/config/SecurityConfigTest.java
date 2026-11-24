package com.orderflow.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SecurityConfigTest {
  @Test
  void rejectsPublishedExampleSigningKey() {
    var config = new SecurityConfig();
    assertThrows(IllegalArgumentException.class,
        () -> config.jwtKey("replace-with-at-least-32-random-bytes"));
  }
}
