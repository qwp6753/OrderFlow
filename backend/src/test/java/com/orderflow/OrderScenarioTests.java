package com.orderflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
abstract class OrderScenarioTests {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired PasswordEncoder passwords;
  @Autowired ObjectMapper json;
  String operator, warehouse, admin;
  static final String PASSWORD = "Test-order-password";

  @BeforeEach
  void setup() throws Exception {
    try (var connection = Objects.requireNonNull(db.getDataSource()).getConnection()) {
      if (connection.getMetaData().getDatabaseProductName().equals("MySQL"))
        assertTrue(connection.getCatalog().endsWith("_test"), "拒绝清理非专用测试库");
    }
    for (String table :
        List.of(
            "return_item",
            "return_request",
            "shipment",
            "stock_movement",
            "order_item",
            "sales_order",
            "stock",
            "sku",
            "product",
            "sys_user_role",
            "sys_user",
            "sys_role")) db.update("DELETE FROM " + table);
    String hash = passwords.encode(PASSWORD);
    int id = 1;
    for (String role : List.of("ADMIN", "OPERATOR", "WAREHOUSE")) {
      db.update(
          "INSERT INTO sys_user(id,username,password_hash,display_name,enabled)"
              + " VALUES(?,?,?,?,true)",
          id,
          role.toLowerCase(Locale.ROOT),
          hash,
          role);
      db.update("INSERT INTO sys_role(id,code,name) VALUES(?,?,?)", id, role, role);
      db.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", id, id);
      id++;
    }
    db.update("INSERT INTO product(id,name,description,status) VALUES(1,'原商品','描述','ON_SALE')");
    for (int sku = 1; sku <= 2; sku++) {
      db.update(
          "INSERT INTO sku(id,product_id,code,name,price,status) VALUES(?,1,?,?,12.50,'ON_SALE')",
          sku,
          "ORDER-SKU-" + sku,
          "原规格" + sku);
      db.update("INSERT INTO stock(sku_id,quantity,reserved_quantity) VALUES(?,10,0)", sku);
    }
    operator = login("operator");
    warehouse = login("warehouse");
    admin = login("admin");
  }

  String login(String username) throws Exception {
    return "Bearer "
        + json.readTree(
                mvc.perform(
                        post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                json.writeValueAsString(
                                    Map.of("username", username, "password", PASSWORD))))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("data")
            .path("token")
            .asText();
  }

  Map<String, Object> line(long sku, int quantity) {
    return Map.of("skuId", sku, "quantity", quantity);
  }

  Map<String, Object> input(List<Map<String, Object>> lines) {
    return new HashMap<>(
        Map.of(
            "recipientName",
            "演示收件人",
            "recipientPhone",
            "13800000000",
            "recipientAddress",
            "测试地址",
            "remark",
            "内部演示",
            "items",
            lines));
  }

  MvcResult create(String auth, Map<String, Object> body) throws Exception {
    return mvc.perform(
            post("/api/orders")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andReturn();
  }

  JsonNode data(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString()).path("data");
  }

  long createId(List<Map<String, Object>> lines) throws Exception {
    var result = create(operator, input(lines));
    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    return data(result).path("id").asLong();
  }

  int reserved(long sku) {
    return db.queryForObject(
        "SELECT reserved_quantity FROM stock WHERE sku_id=?", Integer.class, sku);
  }

  int count(String table) {
    return db.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }

  String state(long id) {
    return db.queryForObject("SELECT status FROM sales_order WHERE id=?", String.class, id);
  }

  MvcResult action(long id, String action, String auth) throws Exception {
    return mvc.perform(post("/api/orders/" + id + "/" + action).header("Authorization", auth))
        .andReturn();
  }

  List<Integer> concurrent(List<Callable<Integer>> calls) throws Exception {
    try (var pool = Executors.newFixedThreadPool(calls.size())) {
      var ready = new CountDownLatch(calls.size());
      var start = new CountDownLatch(1);
      var futures = new ArrayList<Future<Integer>>();
      for (var call : calls)
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("并发启动超时");
                  return call.call();
                }));
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      var result = new ArrayList<Integer>();
      for (var future : futures) result.add(future.get(30, TimeUnit.SECONDS));
      return result;
    }
  }

  @Test
  void createsOrderWithMultipleSkusAndBackendAmount() throws Exception {
    var request = input(List.of(line(2, 3), line(1, 2)));
    request.put("totalAmount", "0.01");
    var result = create(operator, request);
    assertEquals(200, result.getResponse().getStatus());
    var order = data(result);
    assertEquals("PENDING_PAYMENT", order.path("status").asText());
    assertEquals(0, new BigDecimal("62.50").compareTo(order.path("totalAmount").decimalValue()));
    assertTrue(order.path("orderNo").asText().startsWith("OF"));
    assertEquals(2, reserved(1));
    assertEquals(3, reserved(2));
    assertEquals(2, count("order_item"));
    assertEquals(2, count("stock_movement"));
    assertEquals(
        2,
        db.queryForObject(
            "SELECT COUNT(*) FROM stock_movement WHERE order_id=? AND movement_type='RESERVE' AND"
                + " quantity_delta=0 AND reserved_delta>0",
            Integer.class,
            order.path("id").asLong()));
  }

  @Test
  void preservesProductSkuAndPriceSnapshots() throws Exception {
    long id = createId(List.of(line(1, 2)));
    db.update("UPDATE sku SET price=99.99,name='新规格',code='NEW-CODE' WHERE id=1");
    db.update("UPDATE product SET name='新商品' WHERE id=1");
    mvc.perform(get("/api/orders/" + id).header("Authorization", operator))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.order.totalAmount").value(25.0))
        .andExpect(jsonPath("$.data.items[0].unitPrice").value(12.5))
        .andExpect(jsonPath("$.data.items[0].productName").value("原商品"))
        .andExpect(jsonPath("$.data.items[0].skuName").value("原规格1"))
        .andExpect(jsonPath("$.data.items[0].skuCode").value("ORDER-SKU-1"));
  }

  @Test
  void insufficientStockAndMultiSkuFailureRollbackEverything() throws Exception {
    assertEquals(409, create(operator, input(List.of(line(1, 11)))).getResponse().getStatus());
    assertEquals(
        409, create(operator, input(List.of(line(1, 4), line(2, 11)))).getResponse().getStatus());
    assertEquals(0, reserved(1));
    assertEquals(0, reserved(2));
    for (String table : List.of("sales_order", "order_item", "stock_movement"))
      assertEquals(0, count(table));
  }

  @Test
  void concurrentCreatesCannotOversell() throws Exception {
    var calls = new ArrayList<Callable<Integer>>();
    for (int i = 0; i < 8; i++)
      calls.add(() -> create(operator, input(List.of(line(1, 3)))).getResponse().getStatus());
    var results = concurrent(calls);
    assertEquals(3, Collections.frequency(results, 200), results.toString());
    assertEquals(5, Collections.frequency(results, 409), results.toString());
    assertEquals(9, reserved(1));
    assertEquals(3, count("sales_order"));
    assertEquals(3, count("stock_movement"));
    assertEquals(
        9,
        db.queryForObject(
            "SELECT SUM(reserved_delta) FROM stock_movement WHERE sku_id=1", Integer.class));
    assertEquals(
        List.of(3, 6, 9),
        db.queryForList(
            "SELECT reserved_after FROM stock_movement ORDER BY reserved_after", Integer.class));
  }

  @Test
  void cancelsUnpaidAndPaidOrdersOnlyOnce() throws Exception {
    for (boolean paid : List.of(false, true)) {
      long id = createId(List.of(line(1, 2), line(2, 1)));
      if (paid)
        assertEquals(200, action(id, "simulate-payment", operator).getResponse().getStatus());
      assertEquals(200, action(id, "cancel", operator).getResponse().getStatus());
      assertEquals("CANCELED", state(id));
      assertEquals(0, reserved(1));
      assertEquals(0, reserved(2));
      assertEquals(409, action(id, "cancel", operator).getResponse().getStatus());
      assertEquals(0, reserved(1));
      assertEquals(
          2,
          db.queryForObject(
              "SELECT COUNT(*) FROM stock_movement WHERE order_id=? AND movement_type='RELEASE'",
              Integer.class,
              id));
    }
  }

  @Test
  void concurrentCancelsReleaseExactlyOnce() throws Exception {
    long id = createId(List.of(line(1, 4)));
    var calls = new ArrayList<Callable<Integer>>();
    for (int i = 0; i < 6; i++)
      calls.add(() -> action(id, "cancel", operator).getResponse().getStatus());
    var results = concurrent(calls);
    assertEquals(1, Collections.frequency(results, 200), results.toString());
    assertEquals(5, Collections.frequency(results, 409), results.toString());
    assertEquals(0, reserved(1));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM stock_movement WHERE movement_type='RELEASE'", Integer.class));
  }

  @Test
  void paymentAndCancellationRaceHasValidFinalState() throws Exception {
    long id = createId(List.of(line(1, 4)));
    var result =
        concurrent(
            List.of(
                () -> action(id, "simulate-payment", operator).getResponse().getStatus(),
                () -> action(id, "cancel", operator).getResponse().getStatus()));
    assertTrue(result.get(0) == 200 || result.get(0) == 409, result.toString());
    assertEquals(200, result.get(1));
    assertEquals("CANCELED", state(id));
    assertEquals(0, reserved(1));
    assertEquals(2, count("stock_movement"));
  }

  @Test
  void releaseFailureRollsBackStateAndEarlierSkuRelease() throws Exception {
    long id = createId(List.of(line(1, 2), line(2, 2)));
    db.update("UPDATE stock SET reserved_quantity=0 WHERE sku_id=2");
    assertEquals(409, action(id, "cancel", operator).getResponse().getStatus());
    assertEquals("PENDING_PAYMENT", state(id));
    assertEquals(2, reserved(1));
    assertEquals(2, count("stock_movement"));
  }

  @Test
  void rejectsIllegalTransitionsAndUnavailableProducts() throws Exception {
    long id = createId(List.of(line(1, 1)));
    assertEquals(200, action(id, "simulate-payment", operator).getResponse().getStatus());
    assertEquals("PENDING_SHIPMENT", state(id));
    assertEquals(1, reserved(1));
    assertEquals(409, action(id, "simulate-payment", operator).getResponse().getStatus());
    for (String state : List.of("SHIPPED", "COMPLETED", "CANCELED")) {
      db.update("UPDATE sales_order SET status=? WHERE id=?", state, id);
      assertEquals(409, action(id, "simulate-payment", operator).getResponse().getStatus());
      assertEquals(409, action(id, "cancel", operator).getResponse().getStatus());
    }
    db.update("UPDATE product SET status='OFF_SALE' WHERE id=1");
    assertEquals(409, create(operator, input(List.of(line(2, 1)))).getResponse().getStatus());
    db.update("UPDATE product SET status='ON_SALE' WHERE id=1");
    db.update("UPDATE sku SET status='OFF_SALE' WHERE id=2");
    assertEquals(409, create(operator, input(List.of(line(2, 1)))).getResponse().getStatus());
  }

  @Test
  void orderFiltersAndRoleBoundaries() throws Exception {
    long id = createId(List.of(line(1, 1)));
    String number =
        db.queryForObject("SELECT order_no FROM sales_order WHERE id=?", String.class, id);
    mvc.perform(
            get("/api/orders")
                .param("orderNo", number)
                .param("status", "PENDING_PAYMENT")
                .param("from", "2000-01-01T00:00:00")
                .param("to", "2099-01-01T00:00:00")
                .header("Authorization", warehouse))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
    mvc.perform(get("/api/orders/" + id).header("Authorization", warehouse))
        .andExpect(status().isOk());
    assertEquals(403, create(warehouse, input(List.of(line(2, 1)))).getResponse().getStatus());
    assertEquals(403, action(id, "cancel", warehouse).getResponse().getStatus());
    assertEquals(403, action(id, "simulate-payment", warehouse).getResponse().getStatus());
    assertEquals(200, create(admin, input(List.of(line(2, 1)))).getResponse().getStatus());
    assertEquals(200, action(id, "simulate-payment", admin).getResponse().getStatus());
    assertEquals(200, action(id, "cancel", admin).getResponse().getStatus());
    mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
  }

  @Test
  void validatesInputsBeforeBusinessWrites() throws Exception {
    for (var lines :
        List.of(
            List.<Map<String, Object>>of(),
            List.of(line(1, 0)),
            List.of(line(1, -1)),
            List.of(line(1, 1), line(1, 2))))
      assertEquals(400, create(operator, input(lines)).getResponse().getStatus());
    assertEquals(404, create(operator, input(List.of(line(99999, 1)))).getResponse().getStatus());
    var nullLine = input(List.of(line(1, 1)));
    nullLine.put("items", Arrays.asList((Object) null));
    assertEquals(400, create(operator, nullLine).getResponse().getStatus());
    mvc.perform(
            get("/api/orders")
                .param("from", "2027-01-01T00:00:00")
                .param("to", "2020-01-01T00:00:00")
                .header("Authorization", operator))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/orders").param("status", "CREATED").header("Authorization", operator))
        .andExpect(status().isBadRequest());
    assertEquals(404, action(999999, "cancel", operator).getResponse().getStatus());
    assertEquals(0, count("sales_order"));
  }

  @Test
  void receivesPhysicalStockWithMovement() throws Exception {
    mvc.perform(
            post("/api/inventory/1/receipts")
                .header("Authorization", warehouse)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":5,\"remark\":\"采购到货\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.quantityAfter").value(15))
        .andExpect(jsonPath("$.data.quantityDelta").value(5));
    assertEquals(0, reserved(1));
    assertEquals(1, count("stock_movement"));
  }

  int quantity(long sku) {
    return db.queryForObject("SELECT quantity FROM stock WHERE sku_id=?", Integer.class, sku);
  }

  MvcResult receipt(String auth, long sku, Object quantity) throws Exception {
    return mvc.perform(
            post("/api/inventory/" + sku + "/receipts")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("quantity", quantity, "remark", "到货验收"))))
        .andReturn();
  }

  MvcResult ship(long id, String auth) throws Exception {
    return mvc.perform(
            post("/api/orders/" + id + "/ship")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("carrier", "测试物流", "trackingNo", "TRACK-" + id))))
        .andReturn();
  }

  long paidOrder(List<Map<String, Object>> lines) throws Exception {
    long id = createId(lines);
    assertEquals(200, action(id, "simulate-payment", operator).getResponse().getStatus());
    return id;
  }

  @Test
  void rejectsInvalidReceiptsAndIntegerOverflow() throws Exception {
    for (Object quantity : List.of(0, -1, 1.5, 2147483648L))
      assertEquals(400, receipt(warehouse, 1, quantity).getResponse().getStatus());
    assertEquals(404, receipt(warehouse, 99999, 1).getResponse().getStatus());
    db.update("UPDATE stock SET quantity=2147483646 WHERE sku_id=1");
    assertEquals(409, receipt(warehouse, 1, 2).getResponse().getStatus());
    assertEquals(2147483646, quantity(1));
    assertEquals(0, count("stock_movement"));
  }

  @Test
  void concurrentReceiptsPreserveEveryAddition() throws Exception {
    var calls = new ArrayList<Callable<Integer>>();
    for (int i = 0; i < 6; i++) calls.add(() -> receipt(warehouse, 1, 5).getResponse().getStatus());
    var results = concurrent(calls);
    assertEquals(6, Collections.frequency(results, 200), results.toString());
    assertEquals(40, quantity(1));
    assertEquals(0, reserved(1));
    assertEquals(6, count("stock_movement"));
    assertEquals(
        List.of(15, 20, 25, 30, 35, 40),
        db.queryForList(
            "SELECT quantity_after FROM stock_movement ORDER BY quantity_after", Integer.class));
  }

  @Test
  void receiptLedgerFailureRollsBackStock() throws Exception {
    db.execute(
        "ALTER TABLE stock_movement ADD CONSTRAINT ck_receipt_test CHECK (quantity_delta<=0)");
    try {
      int failure = receipt(warehouse, 1, 5).getResponse().getStatus();
      assertTrue(Set.of(409, 500).contains(failure));
      assertEquals(10, quantity(1));
      assertEquals(0, count("stock_movement"));
    } finally {
      try (var connection = db.getDataSource().getConnection()) {
        String drop =
            connection.getMetaData().getDatabaseProductName().equals("MySQL")
                ? "DROP CHECK"
                : "DROP CONSTRAINT";
        db.execute("ALTER TABLE stock_movement " + drop + " ck_receipt_test");
      }
    }
  }

  @Test
  void shippingReducesPhysicalAndReservedButNotAvailableStock() throws Exception {
    db.update("UPDATE stock SET quantity=100 WHERE sku_id=1");
    long id = paidOrder(List.of(line(1, 5)));
    createId(List.of(line(1, 15)));
    assertEquals(20, reserved(1));
    assertEquals(200, ship(id, warehouse).getResponse().getStatus());
    assertEquals("SHIPPED", state(id));
    assertEquals(95, quantity(1));
    assertEquals(15, reserved(1));
    assertEquals(80, quantity(1) - reserved(1));
    assertEquals(1, count("shipment"));
    mvc.perform(get("/api/orders/" + id).header("Authorization", operator))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.shipment.carrier").value("测试物流"))
        .andExpect(jsonPath("$.data.shipment.trackingNo").value("TRACK-" + id))
        .andExpect(jsonPath("$.data.shipment.shippedAt").isNotEmpty());
    assertEquals(
        -5,
        db.queryForObject(
            "SELECT quantity_delta FROM stock_movement WHERE order_id=? AND movement_type='SHIP'",
            Integer.class,
            id));
    assertEquals(
        -5,
        db.queryForObject(
            "SELECT reserved_delta FROM stock_movement WHERE order_id=? AND movement_type='SHIP'",
            Integer.class,
            id));
  }

  @Test
  void repeatedShippingAndPostShippingCancellationDoNotChangeStock() throws Exception {
    long id = paidOrder(List.of(line(1, 3)));
    assertEquals(200, ship(id, warehouse).getResponse().getStatus());
    assertEquals(409, ship(id, warehouse).getResponse().getStatus());
    assertEquals(409, action(id, "cancel", operator).getResponse().getStatus());
    assertEquals(7, quantity(1));
    assertEquals(0, reserved(1));
    assertEquals(1, count("shipment"));
    assertEquals(2, count("stock_movement"));
  }

  @Test
  void concurrentShippingSucceedsExactlyOnce() throws Exception {
    long id = paidOrder(List.of(line(1, 4)));
    var calls = new ArrayList<Callable<Integer>>();
    for (int i = 0; i < 6; i++) calls.add(() -> ship(id, warehouse).getResponse().getStatus());
    var results = concurrent(calls);
    assertEquals(1, Collections.frequency(results, 200), results.toString());
    assertEquals(5, Collections.frequency(results, 409), results.toString());
    assertEquals(6, quantity(1));
    assertEquals(0, reserved(1));
    assertEquals(1, count("shipment"));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM stock_movement WHERE movement_type='SHIP'", Integer.class));
  }

  @Test
  void multiSkuShippingFailureRollsBackEntireFulfillment() throws Exception {
    long id = paidOrder(List.of(line(1, 2), line(2, 2)));
    db.update("UPDATE stock SET reserved_quantity=0 WHERE sku_id=2");
    assertEquals(409, ship(id, warehouse).getResponse().getStatus());
    assertEquals("PENDING_SHIPMENT", state(id));
    assertEquals(10, quantity(1));
    assertEquals(2, reserved(1));
    assertEquals(10, quantity(2));
    assertEquals(0, count("shipment"));
    assertEquals(2, count("stock_movement"));
  }

  @Test
  void cancellationAndShippingRaceHasOneWinner() throws Exception {
    long id = paidOrder(List.of(line(1, 4)));
    var results =
        concurrent(
            List.of(
                () -> ship(id, warehouse).getResponse().getStatus(),
                () -> action(id, "cancel", operator).getResponse().getStatus()));
    assertEquals(1, Collections.frequency(results, 200), results.toString());
    assertEquals(1, Collections.frequency(results, 409), results.toString());
    assertEquals(0, reserved(1));
    assertEquals(2, count("stock_movement"));
    if (state(id).equals("SHIPPED")) {
      assertEquals(6, quantity(1));
      assertEquals(1, count("shipment"));
    } else {
      assertEquals("CANCELED", state(id));
      assertEquals(10, quantity(1));
      assertEquals(0, count("shipment"));
    }
  }

  @Test
  void illegalShippingStatesAndMissingLogisticsAreRejected() throws Exception {
    long id = createId(List.of(line(1, 1)));
    assertEquals(409, ship(id, warehouse).getResponse().getStatus());
    assertEquals(200, action(id, "cancel", operator).getResponse().getStatus());
    assertEquals(409, ship(id, warehouse).getResponse().getStatus());
    long pending = paidOrder(List.of(line(1, 1)));
    mvc.perform(
            post("/api/orders/" + pending + "/ship")
                .header("Authorization", warehouse)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"carrier\":\" \",\"trackingNo\":\"\"}"))
        .andExpect(status().isBadRequest());
    assertEquals("PENDING_SHIPMENT", state(pending));
    assertEquals(10, quantity(1));
    assertEquals(0, count("shipment"));
  }

  @Test
  void completionHasOneEffectAndNeverDeductsStockAgain() throws Exception {
    long id = paidOrder(List.of(line(1, 2)));
    assertEquals(409, action(id, "complete", operator).getResponse().getStatus());
    assertEquals(200, ship(id, warehouse).getResponse().getStatus());
    var results =
        concurrent(
            List.of(
                () -> action(id, "complete", operator).getResponse().getStatus(),
                () -> action(id, "complete", admin).getResponse().getStatus()));
    assertEquals(1, Collections.frequency(results, 200), results.toString());
    assertEquals(1, Collections.frequency(results, 409), results.toString());
    assertEquals("COMPLETED", state(id));
    assertNotNull(
        db.queryForObject(
            "SELECT completed_at FROM sales_order WHERE id=?", java.sql.Timestamp.class, id));
    assertEquals(8, quantity(1));
    assertEquals(0, reserved(1));
    assertEquals(2, count("stock_movement"));
    assertEquals(409, ship(id, warehouse).getResponse().getStatus());
    assertEquals(409, action(id, "cancel", operator).getResponse().getStatus());
  }

  @Test
  void inventoryAndLedgerFiltersUseRealData() throws Exception {
    assertEquals(200, receipt(warehouse, 1, 5).getResponse().getStatus());
    long id = paidOrder(List.of(line(1, 2)));
    assertEquals(200, ship(id, warehouse).getResponse().getStatus());
    mvc.perform(
            get("/api/inventory")
                .param("keyword", "ORDER-SKU-1")
                .param("size", "1")
                .header("Authorization", operator))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.records[0].quantity").value(13))
        .andExpect(jsonPath("$.data.records[0].availableQuantity").value(13));
    mvc.perform(
            get("/api/inventory/movements")
                .param("skuId", "1")
                .param("skuCode", "ORDER-SKU-1")
                .param("type", "RECEIPT")
                .param("from", "2000-01-01T00:00:00")
                .param("to", "2099-01-01T00:00:00")
                .header("Authorization", warehouse))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.records[0].remark").value("到货验收"))
        .andExpect(jsonPath("$.data.records[0].operatorName").value("WAREHOUSE"));
    mvc.perform(
            get("/api/inventory/movements").param("type", "SHIP").header("Authorization", operator))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.records[0].orderId").value(id));
    mvc.perform(
            get("/api/inventory/movements")
                .param("from", "2099-01-01T00:00:00")
                .param("to", "2000-01-01T00:00:00")
                .header("Authorization", warehouse))
        .andExpect(status().isBadRequest());
  }

  @Test
  void warehouseAndOperatorFulfillmentPermissionsAreDistinct() throws Exception {
    assertEquals(403, receipt(operator, 1, 1).getResponse().getStatus());
    assertEquals(200, receipt(admin, 1, 1).getResponse().getStatus());
    long id = paidOrder(List.of(line(1, 1)));
    assertEquals(403, ship(id, operator).getResponse().getStatus());
    assertEquals(200, ship(id, admin).getResponse().getStatus());
    assertEquals(403, action(id, "complete", warehouse).getResponse().getStatus());
    assertEquals(200, action(id, "complete", operator).getResponse().getStatus());
    mvc.perform(get("/api/inventory")).andExpect(status().isUnauthorized());
  }

 @Test void createsReturnFromCompletedOrder() throws Exception {
   long id=paidOrder(List.of(line(1,3)));
   assertEquals(200,ship(id,warehouse).getResponse().getStatus());
   assertEquals(200,action(id,"complete",operator).getResponse().getStatus());
   long item=db.queryForObject("SELECT id FROM order_item WHERE order_id=?",Long.class,id);
   mvc.perform(post("/api/returns").header("Authorization",operator).contentType(MediaType.APPLICATION_JSON)
     .content(json.writeValueAsString(Map.of("orderItemId",item,"quantity",2,"reason","演示退货"))))
     .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));
 }


  long returnOrderItem(int quantity) throws Exception {
    long order=paidOrder(List.of(line(1,quantity)));
    assertEquals(200,ship(order,warehouse).getResponse().getStatus());
    assertEquals(200,action(order,"complete",operator).getResponse().getStatus());
    return db.queryForObject("SELECT id FROM order_item WHERE order_id=?",Long.class,order);
  }

  MvcResult returnPost(String path,String auth,Map<String,?> body) throws Exception {
    return mvc.perform(post("/api/returns"+path).header("Authorization",auth)
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn();
  }

  MvcResult applyReturn(long item,int quantity,String auth) throws Exception {
    return returnPost("",auth,Map.of("orderItemId",item,"quantity",quantity,"reason","退货测试"));
  }

  long returnId(long item,int quantity) throws Exception {
    var response=applyReturn(item,quantity,operator);
    assertEquals(200,response.getResponse().getStatus());
    return data(response).path("id").asLong();
  }

  @Test void returnQuotaAndPriceSnapshot() throws Exception {
    long order=createId(List.of(line(1,5)));
    long item=db.queryForObject("SELECT id FROM order_item WHERE order_id=?",Long.class,order);
    assertEquals(409,applyReturn(item,1,operator).getResponse().getStatus());
    assertEquals(200,action(order,"simulate-payment",operator).getResponse().getStatus());
    assertEquals(200,ship(order,warehouse).getResponse().getStatus());
    assertEquals(200,action(order,"complete",operator).getResponse().getStatus());
    db.update("UPDATE sku SET price=99 WHERE id=1");
    int before=quantity(1);
    long rejected=returnId(item,2);
    long kept=returnId(item,3);
    assertEquals(409,applyReturn(item,1,operator).getResponse().getStatus());
    assertEquals(200,returnPost("/"+rejected+"/reject",operator,Map.of("reason","不符合条件")).getResponse().getStatus());
    assertEquals(409,returnPost("/"+rejected+"/receive",warehouse,Map.of()).getResponse().getStatus());
    returnId(item,2);
    assertEquals(409,applyReturn(item,1,operator).getResponse().getStatus());
    assertEquals(before,quantity(1));
    mvc.perform(get("/api/returns/"+kept).header("Authorization",operator))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.lines[0].item.refundAmount").value(37.5));
  }

  @Test void concurrentReturnApplicationsRespectQuantity() throws Exception {
    long item=returnOrderItem(5);
    var results=concurrent(List.of(()->applyReturn(item,3,operator).getResponse().getStatus(),
        ()->applyReturn(item,3,operator).getResponse().getStatus()));
    assertEquals(1,Collections.frequency(results,200));
    assertEquals(1,Collections.frequency(results,409));
    assertEquals(3,db.queryForObject("SELECT SUM(quantity) FROM return_item",Integer.class));
  }

  @Test void concurrentAcceptanceRestocksOnceAndPreservesReservations() throws Exception {
    long item=returnOrderItem(5);
    long id=returnId(item,3);
    createId(List.of(line(1,1)));
    int before=quantity(1);
    assertEquals(409,returnPost("/"+id+"/receive",warehouse,Map.of()).getResponse().getStatus());
    assertEquals(200,returnPost("/"+id+"/approve",operator,Map.of()).getResponse().getStatus());
    assertEquals(409,returnPost("/"+id+"/approve",operator,Map.of()).getResponse().getStatus());
    assertEquals(200,returnPost("/"+id+"/note",warehouse,Map.of("reason","待复核")).getResponse().getStatus());
    assertEquals(before,quantity(1));
    var results=concurrent(List.of(()->returnPost("/"+id+"/receive",warehouse,Map.of("remark","验收合格")).getResponse().getStatus(),
        ()->returnPost("/"+id+"/receive",warehouse,Map.of()).getResponse().getStatus()));
    assertEquals(1,Collections.frequency(results,200));
    assertEquals(1,Collections.frequency(results,409));
    assertEquals(409,returnPost("/"+id+"/receive",warehouse,Map.of()).getResponse().getStatus());
    assertEquals(before+3,quantity(1));
    assertEquals(1,reserved(1));
    assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM stock_movement WHERE movement_type='RETURN' AND reserved_delta=0 AND quantity_delta=3",Integer.class));
    assertEquals("COMPLETED",db.queryForObject("SELECT status FROM return_request WHERE id=?",String.class,id));
    assertEquals("COMPLETED",db.queryForObject("SELECT o.status FROM sales_order o JOIN return_request r ON r.order_id=o.id WHERE r.id=?",String.class,id));
    assertEquals(409,applyReturn(item,3,operator).getResponse().getStatus());
  }

  @Test void returnReceiptFailureRollsBackStatusAndStock() throws Exception {
    long id=returnId(returnOrderItem(3),2);
    assertEquals(200,returnPost("/"+id+"/approve",operator,Map.of()).getResponse().getStatus());
    db.update("UPDATE stock SET quantity=2147483647 WHERE sku_id=1");
    assertEquals(409,returnPost("/"+id+"/receive",warehouse,Map.of()).getResponse().getStatus());
    assertEquals("PENDING_RECEIPT",db.queryForObject("SELECT status FROM return_request WHERE id=?",String.class,id));
    db.update("UPDATE stock SET quantity=7 WHERE sku_id=1");
    db.execute("ALTER TABLE stock_movement ADD CONSTRAINT ck_return_test CHECK (movement_type <> 'RETURN')");
    try {
      assertTrue(Set.of(409,500).contains(returnPost("/"+id+"/receive",warehouse,Map.of()).getResponse().getStatus()));
      assertEquals(7,quantity(1));
      assertEquals("PENDING_RECEIPT",db.queryForObject("SELECT status FROM return_request WHERE id=?",String.class,id));
      assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM stock_movement WHERE movement_type='RETURN'",Integer.class));
    } finally {
      try(var connection=db.getDataSource().getConnection()) {
        String drop=connection.getMetaData().getDatabaseProductName().equals("MySQL")?"DROP CHECK":"DROP CONSTRAINT";
        db.execute("ALTER TABLE stock_movement "+drop+" ck_return_test");
      }
    }
  }

  @Test void returnPermissionsAndPositiveQuantity() throws Exception {
    long item=returnOrderItem(2);
    assertEquals(403,applyReturn(item,1,warehouse).getResponse().getStatus());
    assertEquals(400,applyReturn(item,0,operator).getResponse().getStatus());
    long id=returnId(item,1);
    assertEquals(403,returnPost("/"+id+"/approve",warehouse,Map.of()).getResponse().getStatus());
    assertEquals(403,returnPost("/"+id+"/reject",warehouse,Map.of("reason","拒绝")).getResponse().getStatus());
    assertEquals(200,returnPost("/"+id+"/approve",admin,Map.of()).getResponse().getStatus());
    assertEquals(403,returnPost("/"+id+"/receive",operator,Map.of()).getResponse().getStatus());
    mvc.perform(get("/api/returns").param("status","PENDING_RECEIPT").header("Authorization",warehouse))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    mvc.perform(get("/api/returns")).andExpect(status().isUnauthorized());
  }


  @Test void dashboardFillsEmptyDays() throws Exception {
    mvc.perform(get("/api/analytics/dashboard").param("from","2026-01-01")
        .param("to","2026-01-03").header("Authorization",operator))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.trend.length()").value(3))
        .andExpect(jsonPath("$.data.trend[1].createdOrders").value(0))
        .andExpect(jsonPath("$.data.netAmount").value(0));
  }


  @Test void dashboardSeparatesDateCohortsAndDoesNotMultiplyAmounts() throws Exception {
    long sold=paidOrder(List.of(line(1,4),line(2,2)));
    assertEquals(200,ship(sold,warehouse).getResponse().getStatus());
    assertEquals(200,action(sold,"complete",operator).getResponse().getStatus());
    long earlierItem=returnOrderItem(2);
    long earlier=db.queryForObject("SELECT order_id FROM order_item WHERE id=?",Long.class,earlierItem);
    db.update("UPDATE sales_order SET created_at='2026-03-01 09:00:00',completed_at='2026-03-01 10:00:00' WHERE id=?",earlier);
    db.update("UPDATE sales_order SET created_at='2026-03-02 00:00:00',completed_at='2026-03-03 23:59:59' WHERE id=?",sold);
    long item=db.queryForObject("SELECT id FROM order_item WHERE order_id=? AND sku_id=1",Long.class,sold);
    long first=returnId(item,1), second=returnId(item,2), oldOrderReturn=returnId(earlierItem,1);
    for(long id:List.of(first,second,oldOrderReturn)){
      assertEquals(200,returnPost("/"+id+"/approve",operator,Map.of()).getResponse().getStatus());
      assertEquals(200,returnPost("/"+id+"/receive",warehouse,Map.of()).getResponse().getStatus());
    }
    db.update("UPDATE return_request SET created_at='2026-03-02 12:00:00',received_at='2026-03-03 10:00:00'");
    db.update("UPDATE return_request SET created_at='2026-03-01 12:00:00' WHERE id=?",oldOrderReturn);
    long rejected=returnId(item,1);
    assertEquals(200,returnPost("/"+rejected+"/reject",operator,Map.of("reason","不符")).getResponse().getStatus());
    long pending=returnId(item,1);
    db.update("UPDATE return_request SET created_at='2026-03-02 13:00:00' WHERE id IN (?,?)",rejected,pending);
    long canceled=createId(List.of(line(2,1)));
    assertEquals(200,action(canceled,"cancel",operator).getResponse().getStatus());
    long unpaid=createId(List.of(line(1,1)));
    long boundary=paidOrder(List.of(line(2,1)));
    db.update("UPDATE sales_order SET created_at='2026-03-02 11:00:00' WHERE id=?",canceled);
    db.update("UPDATE sales_order SET created_at='2026-03-03 11:00:00' WHERE id=?",unpaid);
    db.update("UPDATE sales_order SET created_at='2026-03-04 00:00:00' WHERE id=?",boundary);
    db.update("UPDATE stock SET quantity=11 WHERE sku_id=2");
    db.update("UPDATE sku SET price=999, code='NEW-SKU' WHERE id=1");
    db.update("UPDATE product SET name='新名称' WHERE id=1");

    mvc.perform(get("/api/analytics/dashboard").param("from","2026-03-02").param("to","2026-03-03")
        .header("Authorization",operator))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.orders.total").value(3))
        .andExpect(jsonPath("$.data.orders.byStatus.CANCELED").value(1))
        .andExpect(jsonPath("$.data.orders.byStatus.PENDING_PAYMENT").value(1))
        .andExpect(jsonPath("$.data.orders.byStatus.COMPLETED").value(1))
        .andExpect(jsonPath("$.data.trend[0].createdOrders").value(2))
        .andExpect(jsonPath("$.data.trend[0].completedOrders").value(0))
        .andExpect(jsonPath("$.data.trend[1].completedOrders").value(1))
        .andExpect(jsonPath("$.data.grossAmount").value(75))
        .andExpect(jsonPath("$.data.completedReturns.cases").value(3))
        .andExpect(jsonPath("$.data.completedReturns.units").value(4))
        .andExpect(jsonPath("$.data.completedReturns.amount").value(50))
        .andExpect(jsonPath("$.data.netAmount").value(25))
        .andExpect(jsonPath("$.data.afterSales.total").value(4))
        .andExpect(jsonPath("$.data.afterSales.byStatus.REJECTED").value(1))
        .andExpect(jsonPath("$.data.afterSales.byStatus.COMPLETED").value(2))
        .andExpect(jsonPath("$.data.pendingReturns").value(1))
        .andExpect(jsonPath("$.data.inventory.skuCount").value(2))
        .andExpect(jsonPath("$.data.inventory.available").value(17))
        .andExpect(jsonPath("$.data.inventory.lowCount").value(1))
        .andExpect(jsonPath("$.data.topSkus[0].skuCode").value("ORDER-SKU-1"))
        .andExpect(jsonPath("$.data.topSkus[0].productName").value("原商品"))
        .andExpect(jsonPath("$.data.topSkus[0].sold").value(4))
        .andExpect(jsonPath("$.data.topSkus[0].returned").value(4))
        .andExpect(jsonPath("$.data.topSkus[0].netSold").value(0))
        .andExpect(jsonPath("$.data.topSkus[0].amount").value(50))
        .andExpect(jsonPath("$.data.topProducts.length()").value(1))
        .andExpect(jsonPath("$.data.topProducts[0].sold").value(6))
        .andExpect(jsonPath("$.data.topProducts[0].netSold").value(2))
        .andExpect(jsonPath("$.data.topProducts[0].amount").value(75));
    mvc.perform(get("/api/analytics/low-stock").param("size","1").header("Authorization",admin))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.records[0].available").value(7));
  }

  @Test void dashboardProtectsBothEndpointsAndValidatesRange() throws Exception {
    for(String path:List.of("/dashboard","/low-stock")) {
      mvc.perform(get("/api/analytics"+path)).andExpect(status().isUnauthorized());
      mvc.perform(get("/api/analytics"+path).header("Authorization",warehouse)).andExpect(status().isForbidden());
    }
    mvc.perform(get("/api/analytics/dashboard").param("days","30").header("Authorization",admin))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.trend.length()").value(30));
    mvc.perform(get("/api/analytics/dashboard").param("from","2026-03-01").header("Authorization",operator))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/analytics/dashboard").param("from","2025-01-01").param("to","2026-03-01")
        .header("Authorization",operator)).andExpect(status().isBadRequest());
  }


  @Test
  void availabilityCountsEachLineAndExcludesRejectedApplications() throws Exception {
    long order = paidOrder(List.of(line(1,4), line(2,3)));
    assertEquals(200, ship(order,warehouse).getResponse().getStatus());
    assertEquals(200, action(order,"complete",operator).getResponse().getStatus());
    long first = db.queryForObject("SELECT id FROM order_item WHERE order_id=? AND sku_id=1", Long.class, order);
    long second = db.queryForObject("SELECT id FROM order_item WHERE order_id=? AND sku_id=2", Long.class, order);
    returnId(first, 1);
    long rejected = returnId(first, 2);
    assertEquals(200, returnPost("/"+rejected+"/reject", operator, Map.of("reason","拒绝")).getResponse().getStatus());
    returnId(second, 2);

    mvc.perform(get("/api/returns/availability").param("orderId", Long.toString(order))
        .header("Authorization",operator))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.item.skuId == 1)].remaining").value(org.hamcrest.Matchers.contains(3)))
        .andExpect(jsonPath("$.data[?(@.item.skuId == 2)].remaining").value(org.hamcrest.Matchers.contains(1)));
  }
}
