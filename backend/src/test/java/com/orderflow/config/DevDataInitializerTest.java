package com.orderflow.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DevDataInitializerTest {
  @Test
  void rejectsPublishedExampleSeedPasswordBeforeWritingAccounts() {
    var initializer = new DevDataInitializer(null, null);
    ReflectionTestUtils.setField(initializer, "password", "replace-with-your-local-password");
    assertThrows(IllegalArgumentException.class, () -> initializer.run(null));
  }
}
