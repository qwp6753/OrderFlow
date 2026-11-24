package com.orderflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderFlowIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired PasswordEncoder passwords;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;
  static final String PASSWORD = "Test-password-2026";

  @BeforeEach
  void setup() {
    for (String table : List.of("stock", "sku", "product", "sys_user_role", "sys_user", "sys_role"))
      db.update("DELETE FROM " + table);
    int id = 1;
    for (String role : List.of("ADMIN", "OPERATOR", "WAREHOUSE")) {
      db.update("INSERT INTO sys_role(id,code,name) VALUES(?,?,?)", id, role, role);
      db.update(
          "INSERT INTO sys_user(id,username,password_hash,display_name,enabled)"
              + " VALUES(?,?,?,?,true)",
          id,
          role.toLowerCase(Locale.ROOT),
          passwords.encode(PASSWORD),
          role);
      db.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", id, id);
      id++;
    }
  }

  String body(Object data) throws Exception {
    return json.writeValueAsString(data);
  }

  String token(String role) throws Exception {
    String response =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("username", role, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return "Bearer " + json.readTree(response).path("data").path("token").asText();
  }

  long product(String auth) throws Exception {
    var response =
        mvc.perform(
                post("/api/products")
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("name", "测试商品", "description", "事务测试"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("OFF_SALE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response).path("data").path("id").asLong();
  }

  Map<String, Object> sku(String code) {
    return Map.of("code", code, "name", "标准款", "price", "19.90", "status", "ON_SALE");
  }

  @Test
  void loginAndWrongPassword() throws Exception {
    String auth = token("admin");
    mvc.perform(get("/api/auth/me").header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"))
        .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("username", "admin", "password", "wrong"))))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("username", "nobody", "password", PASSWORD))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void authenticationAndRoleBoundaries() throws Exception {
    mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/products").header("Authorization", "Bearer malformed"))
        .andExpect(status().isUnauthorized());
    String warehouse = token("warehouse"), operator = token("operator"), admin = token("admin");
    mvc.perform(get("/api/products").header("Authorization", warehouse)).andExpect(status().isOk());
    mvc.perform(
            post("/api/products")
                .header("Authorization", warehouse)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "禁止创建", "description", ""))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/users").header("Authorization", operator))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/users").header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist());
  }

  @Test
  void createEditFilterAndStatus() throws Exception {
    String auth = token("operator");
    long id = product(auth);
    mvc.perform(
            put("/api/products/" + id)
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "改名商品", "description", "更新描述"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("改名商品"));
    mvc.perform(
            patch("/api/products/" + id + "/status")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("status", "ON_SALE"))))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/products")
                .param("name", "改名")
                .param("status", "ON_SALE")
                .header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
    mvc.perform(get("/api/products/999999").header("Authorization", auth))
        .andExpect(status().isNotFound());
  }

  @Test
  void duplicateSkuRollsBackAndInventoryIsReadOnly() throws Exception {
    String auth = token("operator");
    long id = product(auth);
    var first =
        mvc.perform(
                post("/api/products/" + id + "/skus")
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(sku("TEST-001"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long skuId = json.readTree(first).path("data").path("id").asLong();
    mvc.perform(
            post("/api/products/" + id + "/skus")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(sku("TEST-001"))))
        .andExpect(status().isConflict());
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM sku", Integer.class));
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM stock", Integer.class));
    mvc.perform(get("/api/products/" + id + "/skus").header("Authorization", token("warehouse")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].availableQuantity").value(0));
    mvc.perform(
            put("/api/products/" + id + "/skus/" + skuId)
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        Map.of(
                            "code",
                            "TEST-001",
                            "name",
                            "新版",
                            "price",
                            "29.99",
                            "status",
                            "OFF_SALE"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.price").value(29.99));
    long other = product(auth);
    mvc.perform(
            put("/api/products/" + other + "/skus/" + skuId)
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(sku("TEST-001"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void expiredOrWrongIssuerTokenIsRejected() throws Exception {
    for (String issuer : List.of("orderflow", "untrusted")) {
      var now = java.time.Instant.now();
      var claims =
          org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
              .issuer(issuer)
              .subject("admin")
              .issuedAt(now.minusSeconds(7200))
              .expiresAt(issuer.equals("orderflow") ? now.minusSeconds(300) : now.plusSeconds(3600))
              .build();
      String value =
          jwtEncoder
              .encode(
                  org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                      org.springframework.security.oauth2.jwt.JwsHeader.with(
                              org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                          .build(),
                      claims))
              .getTokenValue();
      mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + value))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  void parameterValidation() throws Exception {
    String auth = token("operator");
    mvc.perform(get("/api/unknown").header("Authorization", auth)).andExpect(status().isNotFound());
    mvc.perform(post("/api/auth/me").header("Authorization", auth))
        .andExpect(status().isMethodNotAllowed());
    mvc.perform(
            post("/api/products")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", " ", "description", ""))))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/products").param("size", "101").header("Authorization", auth))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/products").param("status", "INVALID").header("Authorization", auth))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("username", "admin", "password", "密".repeat(30)))))
        .andExpect(status().isBadRequest());
    long id = product(auth);
    for (String price : List.of("0", "-1", "1.001"))
      mvc.perform(
              post("/api/products/" + id + "/skus")
                  .header("Authorization", auth)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      body(
                          Map.of(
                              "code", "VALID", "name", "标准", "price", price, "status", "ON_SALE"))))
          .andExpect(status().isBadRequest());
  }

  @Test
  void changedRolesAndDisabledAccountsTakeEffectImmediately() throws Exception {
    String auth = token("admin");
    db.update("DELETE FROM sys_user_role WHERE user_id=1");
    mvc.perform(get("/api/admin/users").header("Authorization", auth))
        .andExpect(status().isForbidden());
    db.update("UPDATE sys_user SET enabled=false WHERE username='admin'");
    mvc.perform(get("/api/auth/me").header("Authorization", auth))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void openApiIsAvailableInTestProfile() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/products']").exists());
  }
}
