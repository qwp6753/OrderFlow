package com.orderflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.orderflow.catalog.CatalogRequests.ProductInput;
import com.orderflow.catalog.CatalogService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("mysql-test")
@EnabledIfEnvironmentVariable(named = "ORDERFLOW_MYSQL_TEST_URL", matches = "jdbc:mysql:.*")
class OrderMySqlIntegrationTest extends OrderScenarioTests {
  @Autowired CatalogService catalog;
  @Autowired PlatformTransactionManager transactions;

  @Test
  void productEditsPreserveConcurrentChangesToOtherFields() throws Exception {
    var snapshot = new TransactionTemplate(transactions);
    snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    try (var other = Executors.newSingleThreadExecutor()) {
      snapshot.executeWithoutResult(tx -> {
        assertEquals("ON_SALE", catalog.detail(1L).getStatus());
        try {
          other.submit(() -> catalog.status(1L, "OFF_SALE")).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
          throw new AssertionError("无法完成并发商品状态更新", e);
        }
        catalog.update(1L, new ProductInput("更新名称", "更新说明"));
      });
      assertEquals("OFF_SALE", catalog.detail(1L).getStatus());
      assertEquals("更新名称", catalog.detail(1L).getName());

      snapshot.executeWithoutResult(tx -> {
        assertEquals("更新名称", catalog.detail(1L).getName());
        try {
          other.submit(() -> catalog.update(1L, new ProductInput("并发名称", "并发说明"))).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
          throw new AssertionError("无法完成并发商品资料更新", e);
        }
        catalog.status(1L, "ON_SALE");
      });
      assertEquals("并发名称", catalog.detail(1L).getName());
      assertEquals("ON_SALE", catalog.detail(1L).getStatus());
    }
  }
}
