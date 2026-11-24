package com.orderflow.catalog;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class CatalogRequests {
  private CatalogRequests() {}

  public record ProductInput(
      @NotBlank(message = "商品名称不能为空") @Size(max = 100) String name,
      @NotNull @Size(max = 2000) String description) {}

  public record StatusInput(
      @NotNull @Pattern(regexp = "ON_SALE|OFF_SALE", message = "状态必须为 ON_SALE 或 OFF_SALE")
          String status) {}

  public record SkuInput(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "SKU 编码仅允许1至64位字母、数字、下划线和短横线")
          String code,
      @NotBlank @Size(max = 100) String name,
      @NotNull @DecimalMin(value = "0.01", message = "价格必须大于0") @Digits(integer = 10, fraction = 2)
          BigDecimal price,
      @NotNull @Pattern(regexp = "ON_SALE|OFF_SALE") String status) {}
}
