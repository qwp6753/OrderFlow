package com.orderflow.inventory;

import java.time.LocalDateTime;

public final class InventoryViews {
  private InventoryViews() {}

  public record StockRow(
      Long skuId,
      String productName,
      String skuCode,
      String skuName,
      String skuStatus,
      Integer quantity,
      Integer reservedQuantity,
      Integer availableQuantity) {}

  public record MovementRow(
      Long id,
      Long skuId,
      String skuCode,
      Long orderId,
      String orderNo,
      String movementType,
      Integer quantityDelta,
      Integer reservedDelta,
      Integer quantityAfter,
      Integer reservedAfter,
      String remark,
      String operatorName,
      LocalDateTime createdAt) {}
}
