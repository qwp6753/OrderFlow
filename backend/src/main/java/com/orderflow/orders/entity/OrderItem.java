package com.orderflow.orders.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import lombok.Data;

@Data
@TableName("order_item")
public class OrderItem {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long orderId;
  private Long skuId;
  private String productName;
  private String skuCode;
  private String skuName;
  private BigDecimal unitPrice;
  private Integer quantity;
}
