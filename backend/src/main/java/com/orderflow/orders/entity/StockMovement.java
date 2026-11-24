package com.orderflow.orders.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("stock_movement")
public class StockMovement {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long skuId;
  private Long orderId;
  private String movementType;
  private Integer quantityDelta;
  private Integer reservedDelta;
  private Integer quantityAfter;
  private Integer reservedAfter;
  private String businessKey;
  private Long operatorId;
  private LocalDateTime createdAt;
  private String remark = "";
}
