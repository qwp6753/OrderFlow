package com.orderflow.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AnalyticsViews {
  private AnalyticsViews() {}

  public record CreatedDay(LocalDate bucketDate, String status, long amount) {}
  public record CompletedDay(LocalDate bucketDate, long amount, BigDecimal revenue) {}
  public record StatusCount(String status, long amount) {}
  public record Counts(long total, Map<String, Long> byStatus) {}
  public record Returns(long cases, long units, BigDecimal amount) {}
  public record Stock(long skuCount, long physical, long reserved, long available, long lowCount) {}
  public record LowSku(long skuId, String productName, String skuCode, long physical, long reserved, long available) {}
  public record TopSku(long skuId, String productName, String skuCode, long sold, long returned, long netSold, BigDecimal amount) {}
  public record TopProduct(long productId, String productName, long sold, long returned, long netSold, BigDecimal amount) {}
  public record Trend(LocalDate date, long createdOrders, long completedOrders, BigDecimal amount) {}
  public record Dashboard(LocalDate from, LocalDate to, Counts orders, Counts afterSales,
      Returns completedReturns, long pendingReturns, BigDecimal grossAmount, BigDecimal netAmount,
      Stock inventory, int lowStockThreshold, List<Trend> trend, List<TopSku> topSkus, List<TopProduct> topProducts) {}
}
