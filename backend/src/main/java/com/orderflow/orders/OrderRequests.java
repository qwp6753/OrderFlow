package com.orderflow.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class OrderRequests {
  private OrderRequests() {}

  public record Line(
      @NotNull @Positive Long skuId, @NotNull @Min(1) @Max(1000000) Integer quantity) {}

  public record CreateOrder(
      @NotBlank @Size(max = 100) String recipientName,
      @NotBlank @Size(max = 30) String recipientPhone,
      @NotBlank @Size(max = 500) String recipientAddress,
      @NotNull @Size(max = 500) String remark,
      @NotEmpty @Size(max = 50) List<@NotNull @Valid Line> items) {}

  public record ShipOrder(
      @NotBlank @Size(max = 80) String carrier, @NotBlank @Size(max = 80) String trackingNo) {}
}
