package com.orderflow.orders;

import static com.orderflow.orders.OrderRequests.*;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orderflow.catalog.entity.*;
import com.orderflow.catalog.mapper.*;
import com.orderflow.common.BusinessException;
import com.orderflow.orders.entity.*;
import com.orderflow.orders.mapper.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
  private final OrderMapper orders;
  private final OrderItemMapper items;
  private final StockMovementMapper movements;
  private final ProductMapper products;
  private final SkuMapper skus;
  private final StockMapper stocks;
  private final ShipmentMapper shipments;

  public record Detail(
      SalesOrder order, List<OrderItem> items, List<StockMovement> movements, Shipment shipment) {}

  public Page<SalesOrder> page(
      int page,
      int size,
      String orderNo,
      OrderStatus status,
      LocalDateTime from,
      LocalDateTime to) {
    if (from != null && to != null && from.isAfter(to))
      throw new BusinessException(HttpStatus.BAD_REQUEST, "开始时间不能晚于结束时间");
    return orders.selectPage(
        new Page<>(page, size),
        Wrappers.<SalesOrder>lambdaQuery()
            .eq(orderNo != null && !orderNo.isBlank(), SalesOrder::getOrderNo, orderNo)
            .eq(status != null, SalesOrder::getStatus, status == null ? null : status.name())
            .ge(from != null, SalesOrder::getCreatedAt, from)
            .le(to != null, SalesOrder::getCreatedAt, to)
            .orderByDesc(SalesOrder::getCreatedAt)
            .orderByDesc(SalesOrder::getId));
  }

  @Transactional(readOnly = true)
  public Detail detail(Long id) {
    var order = orders.selectById(id);
    if (order == null) throw BusinessException.missing();
    return new Detail(
        order,
        items.selectList(
            Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, id)
                .orderByAsc(OrderItem::getSkuId)),
        movements.selectList(
            Wrappers.<StockMovement>lambdaQuery()
                .eq(StockMovement::getOrderId, id)
                .orderByAsc(StockMovement::getId)),
        shipments.selectOne(Wrappers.<Shipment>lambdaQuery().eq(Shipment::getOrderId, id)));
  }

  @Transactional
  public SalesOrder create(CreateOrder input, Long userId) {
    var lines = input.items().stream().sorted(Comparator.comparing(Line::skuId)).toList();
    if (lines.stream().map(Line::skuId).distinct().count() != lines.size())
      throw new BusinessException(HttpStatus.BAD_REQUEST, "同一 SKU 请合并数量，不要重复添加");
    var order = new SalesOrder();
    order.setOrderNo("OF" + UUID.randomUUID().toString().replace("-", ""));
    order.setStatus(OrderStatus.PENDING_PAYMENT.name());
    order.setRecipientName(input.recipientName());
    order.setRecipientPhone(input.recipientPhone());
    order.setRecipientAddress(input.recipientAddress());
    order.setRemark(input.remark());
    order.setCreatedBy(userId);
    order.setCreatedAt(LocalDateTime.now(BUSINESS_ZONE));

    // 批量查询 SKU 和 Product，避免循环内 N+1 查询
    var skuIds = lines.stream().map(Line::skuId).toList();
    var skuMap = skus.selectBatchIds(skuIds).stream()
            .collect(java.util.stream.Collectors.toMap(Sku::getId, s -> s));
    var productIds = skuMap.values().stream().map(Sku::getProductId).distinct().toList();
    var productMap = productIds.isEmpty()
            ? Map.<Long, Product>of()
            : products.selectBatchIds(productIds).stream()
            .collect(java.util.stream.Collectors.toMap(Product::getId, p -> p));

    var snapshots = new ArrayList<OrderItem>();
    var total = BigDecimal.ZERO;
    for (var line : lines) {
      var sku = skuMap.get(line.skuId());
      if (sku == null) throw new BusinessException(HttpStatus.NOT_FOUND, "SKU 不存在：" + line.skuId());
      var product = productMap.get(sku.getProductId());
      if (product == null || !"ON_SALE".equals(sku.getStatus()) || !"ON_SALE".equals(product.getStatus()))
        throw new BusinessException(HttpStatus.CONFLICT, "商品或 SKU 已停止销售：" + sku.getCode());
      var item = new OrderItem();
      item.setSkuId(sku.getId());
      item.setProductName(product.getName());
      item.setSkuCode(sku.getCode());
      item.setSkuName(sku.getName());
      item.setUnitPrice(sku.getPrice());
      item.setQuantity(line.quantity());
      snapshots.add(item);
      total = total.add(sku.getPrice().multiply(BigDecimal.valueOf(line.quantity())));
    }
    if (total.compareTo(new BigDecimal("999999999999.99")) > 0)
      throw new BusinessException(HttpStatus.BAD_REQUEST, "订单总金额超出允许范围");
    order.setTotalAmount(total);
    orders.insert(order);
    for (var item : snapshots) {
      item.setOrderId(order.getId());
      items.insert(item);
      if (stocks.reserve(item.getSkuId(), item.getQuantity()) != 1)
        throw new BusinessException(HttpStatus.CONFLICT, "SKU 可用库存不足：" + item.getSkuCode());
      recordMovement(order.getId(), item, "RESERVE", 0, item.getQuantity(), userId);
    }
    return order;
  }

  @Transactional
  public SalesOrder pay(Long id) {
    if (orders.pay(id) != 1) throw transitionFailure(id, "仅待付款订单允许模拟确认付款");
    return orders.selectById(id);
  }

  @Transactional
  public SalesOrder cancel(Long id, Long userId) {
    if (orders.cancel(id) != 1) throw transitionFailure(id, "当前订单不允许取消或已取消");
    var lines =
        items.selectList(
            Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, id)
                .orderByAsc(OrderItem::getSkuId));
    for (var item : lines) {
      if (stocks.release(item.getSkuId(), item.getQuantity()) != 1)
        throw new BusinessException(HttpStatus.CONFLICT, "预占库存不一致，取消已回滚，请核对库存");
      recordMovement(id, item, "RELEASE", 0, -item.getQuantity(), userId);
    }
    return orders.selectById(id);
  }

  @Transactional
  public SalesOrder ship(Long id, ShipOrder input, Long userId) {
    if (orders.ship(id) != 1) throw transitionFailure(id, "仅待发货订单允许发货");
    var lines =
        items.selectList(
            Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, id)
                .orderByAsc(OrderItem::getSkuId));
    for (var item : lines) {
      // quantity>=reserved_quantity由数据库约束保证；同减后可用量保持不变。
      if (stocks.ship(item.getSkuId(), item.getQuantity()) != 1)
        throw new BusinessException(HttpStatus.CONFLICT, "预占库存不足，发货已回滚：" + item.getSkuCode());
      recordMovement(id, item, "SHIP", -item.getQuantity(), -item.getQuantity(), userId);
    }
    var shipment = new Shipment();
    shipment.setOrderId(id);
    shipment.setStatus("SHIPPED");
    shipment.setCarrier(input.carrier());
    shipment.setTrackingNo(input.trackingNo());
    shipment.setShippedBy(userId);
    shipment.setShippedAt(LocalDateTime.now(BUSINESS_ZONE));
    shipments.insert(shipment);
    return orders.selectById(id);
  }

  @Transactional
  public SalesOrder complete(Long id) {
    if (orders.complete(id, LocalDateTime.now(BUSINESS_ZONE)) != 1)
      throw transitionFailure(id, "仅已发货订单允许模拟确认收货");
    return orders.selectById(id);
  }

  private BusinessException transitionFailure(Long id, String message) {
    return orders.selectById(id) == null
        ? BusinessException.missing()
        : new BusinessException(HttpStatus.CONFLICT, message);
  }

  private void recordMovement(
      Long orderId,
      OrderItem item,
      String type,
      int quantityDelta,
      int reservedDelta,
      Long userId) {
    // 条件更新已持有该库存行的排他锁；锁定读取得当前余额，避免REPEATABLE READ的旧快照。
    var stock = stocks.locked(item.getSkuId());
    var movement = new StockMovement();
    movement.setOrderId(orderId);
    movement.setSkuId(item.getSkuId());
    movement.setMovementType(type);
    movement.setQuantityDelta(quantityDelta);
    movement.setReservedDelta(reservedDelta);
    movement.setQuantityAfter(stock.getQuantity());
    movement.setReservedAfter(stock.getReservedQuantity());
    movement.setBusinessKey(type + ":" + item.getId());
    movement.setOperatorId(userId);
    movement.setCreatedAt(LocalDateTime.now(BUSINESS_ZONE));
    movements.insert(movement);
  }
}
