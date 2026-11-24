package com.orderflow.returns;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orderflow.common.BusinessException;
import com.orderflow.inventory.InventoryService;
import com.orderflow.orders.entity.OrderItem;
import com.orderflow.orders.mapper.OrderItemMapper;
import com.orderflow.orders.mapper.OrderMapper;
import com.orderflow.returns.entity.ReturnItem;
import com.orderflow.returns.entity.ReturnRequest;
import com.orderflow.returns.mapper.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReturnService {
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
  private final ReturnRequestMapper requests;
  private final ReturnItemMapper items;
  private final OrderMapper orders;
  private final OrderItemMapper orderItems;
  private final InventoryService inventory;

  public record Line(ReturnItem item, OrderItem original) {}
  public record Detail(ReturnRequest request, List<Line> lines) {}
  public record Available(OrderItem item, long remaining) {}

  public Page<ReturnRequest> page(int page, int size, Long orderId, String status) {
    return requests.selectPage(new Page<>(page, size), Wrappers.<ReturnRequest>lambdaQuery()
        .eq(orderId != null, ReturnRequest::getOrderId, orderId)
        .eq(status != null, ReturnRequest::getStatus, status)
        .orderByDesc(ReturnRequest::getId));
  }

  @Transactional(readOnly = true)
  public Detail detail(Long id) {
    var request = requests.selectById(id);
    if (request == null) throw BusinessException.missing();
    return new Detail(request, lines(id));
  }

  @Transactional(readOnly = true)
  public List<Available> availability(Long orderId) {
    requireCompleted(orderId);
    var claimed = items.claimedForOrder(orderId).stream().collect(Collectors.toMap(
        ReturnItemMapper.ClaimedQuantity::orderItemId, ReturnItemMapper.ClaimedQuantity::quantity));
    return orderItems.selectList(Wrappers.<OrderItem>lambdaQuery().eq(OrderItem::getOrderId, orderId))
        .stream().map(item -> new Available(item, item.getQuantity() - claimed.getOrDefault(item.getId(), 0L))).toList();
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public ReturnRequest create(Long orderItemId, int quantity, String reason, Long userId) {
    // 所有针对同一明细的新申请先锁同一原订单行，再读取已提交的占用量。
    var original = items.lockOrderItem(orderItemId);
    if (original == null) throw BusinessException.missing();
    requireCompleted(original.getOrderId());
    if (items.claimed(orderItemId) + quantity > original.getQuantity())
      throw conflict("退货数量超过剩余可退数量");

    var request = new ReturnRequest();
    request.setReturnNo("RT" + UUID.randomUUID().toString().replace("-", ""));
    request.setOrderId(original.getOrderId());
    request.setStatus("PENDING_REVIEW");
    request.setReason(reason);
    request.setCreatedBy(userId);
    request.setCreatedAt(LocalDateTime.now(BUSINESS_ZONE));
    request.setUpdatedAt(request.getCreatedAt());
    requests.insert(request);

    var item = new ReturnItem();
    item.setReturnId(request.getId());
    item.setOrderItemId(orderItemId);
    item.setQuantity(quantity);
    item.setRefundAmount(original.getUnitPrice().multiply(BigDecimal.valueOf(quantity)));
    items.insert(item);
    return request;
  }

  @Transactional
  public void review(Long id, String rejectionReason, Long userId) {
    boolean rejected = rejectionReason != null;
    requireChange(requests.update(null, Wrappers.<ReturnRequest>lambdaUpdate()
        .eq(ReturnRequest::getId, id).eq(ReturnRequest::getStatus, "PENDING_REVIEW")
        .set(ReturnRequest::getStatus, rejected ? "REJECTED" : "PENDING_RECEIPT")
        .set(ReturnRequest::getRejectionReason, rejectionReason)
        .set(ReturnRequest::getReviewedBy, userId)
        .set(ReturnRequest::getReviewedAt, LocalDateTime.now(BUSINESS_ZONE))
        .set(ReturnRequest::getUpdatedAt, LocalDateTime.now(BUSINESS_ZONE))), id);
  }

  @Transactional
  public void note(Long id, String remark, Long userId) {
    requireChange(requests.update(null, Wrappers.<ReturnRequest>lambdaUpdate()
        .eq(ReturnRequest::getId, id).eq(ReturnRequest::getStatus, "PENDING_RECEIPT")
        .set(ReturnRequest::getReceiptRemark, remark)
        .set(ReturnRequest::getReceivedBy, userId)
        .set(ReturnRequest::getUpdatedAt, LocalDateTime.now(BUSINESS_ZONE))), id);
  }

  @Transactional
  public void receive(Long id, String remark, Long userId) {
    requireChange(requests.update(null, Wrappers.<ReturnRequest>lambdaUpdate()
        .eq(ReturnRequest::getId, id).eq(ReturnRequest::getStatus, "PENDING_RECEIPT")
        .set(ReturnRequest::getStatus, "COMPLETED")
        .set(ReturnRequest::getReceiptRemark, remark)
        .set(ReturnRequest::getReceivedBy, userId)
        .set(ReturnRequest::getReceivedAt, LocalDateTime.now(BUSINESS_ZONE))
        .set(ReturnRequest::getUpdatedAt, LocalDateTime.now(BUSINESS_ZONE))), id);
    var lines = lines(id);
    if (lines.isEmpty()) throw conflict("售后单缺少退货明细");
    // 兼容旧表中已有的多明细单，按 SKU 顺序锁定库存。
    for (var line : lines.stream().sorted(java.util.Comparator.comparing(l -> l.original().getSkuId())).toList()) {
      inventory.receiveReturn(line.original().getSkuId(), line.item().getQuantity(), remark, userId,
          line.original().getOrderId(), line.item().getId());
    }
  }

  private List<Line> lines(Long id) {
    var returnItems = items.selectList(Wrappers.<ReturnItem>lambdaQuery().eq(ReturnItem::getReturnId, id));
    if (returnItems.isEmpty()) return List.of();
    var orderItemIds = returnItems.stream().map(ReturnItem::getOrderItemId).distinct().toList();
    var orderItemMap = orderItems.selectByIds(orderItemIds).stream()
            .collect(Collectors.toMap(OrderItem::getId, oi -> oi));
    return returnItems.stream()
            .map(item -> new Line(item, orderItemMap.get(item.getOrderItemId())))
            .toList();
  }

  private void requireCompleted(Long id) {
    var order = orders.selectById(id);
    if (order == null) throw BusinessException.missing();
    if (!"COMPLETED".equals(order.getStatus())) throw conflict("仅已完成订单允许申请退货");
  }

  private void requireChange(int count, Long id) {
    if (count == 1) return;
    if (requests.selectById(id) == null) throw BusinessException.missing();
    throw conflict("当前售后状态不允许该操作");
  }

  private BusinessException conflict(String message) {
    return new BusinessException(HttpStatus.CONFLICT, message);
  }
}
