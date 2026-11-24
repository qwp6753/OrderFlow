package com.orderflow.inventory;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orderflow.catalog.mapper.StockMapper;
import com.orderflow.common.BusinessException;
import com.orderflow.inventory.mapper.InventoryQueryMapper;
import com.orderflow.orders.entity.StockMovement;
import com.orderflow.orders.mapper.StockMovementMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
  private final StockMapper stocks;
  private final StockMovementMapper movements;
  private final InventoryQueryMapper queries;

  public Page<InventoryViews.StockRow> stocks(int page, int size, String keyword) {
    return queries.stocks(new Page<>(page, size), keyword);
  }

  public Page<InventoryViews.MovementRow> movements(
          int page,
          int size,
          Long skuId,
          String skuCode,
          String type,
          LocalDateTime from,
          LocalDateTime to) {
    if (from != null && to != null && from.isAfter(to))
      throw new BusinessException(HttpStatus.BAD_REQUEST, "开始时间不能晚于结束时间");
    return queries.movements(new Page<>(page, size), skuId, skuCode, type, from, to);
  }

  @Transactional
  public StockMovement receive(Long skuId, int quantity, String remark, Long userId) {
    return addStock(skuId, quantity, remark, userId, null, "RECEIPT", "RECEIPT:" + UUID.randomUUID());
  }

  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void receiveReturn(Long skuId, int quantity, String remark, Long userId, Long orderId, Long returnItemId) {
    addStock(skuId, quantity, remark, userId, orderId, "RETURN", "RETURN:" + returnItemId);
  }

  private StockMovement addStock(Long skuId, int quantity, String remark, Long userId,
                                 Long orderId, String type, String key) {
    if (stocks.receive(skuId, quantity) != 1) {
      if (stocks.locked(skuId) == null) throw BusinessException.missing();
      throw new BusinessException(HttpStatus.CONFLICT, "入库后实物库存超出允许范围");
    }
    var stock = stocks.locked(skuId);
    var movement = new StockMovement();
    movement.setSkuId(skuId);
    movement.setMovementType(type);
    movement.setOrderId(orderId);
    movement.setQuantityDelta(quantity);
    movement.setReservedDelta(0);
    movement.setQuantityAfter(stock.getQuantity());
    movement.setReservedAfter(stock.getReservedQuantity());
    movement.setBusinessKey(key);
    movement.setOperatorId(userId);
    movement.setRemark(remark);
    movement.setCreatedAt(LocalDateTime.now(BUSINESS_ZONE));
    movements.insert(movement);
    return movement;
  }
}