package com.orderflow.orders.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sales_order")
public class SalesOrder {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String orderNo;
  private String status;
  private String recipientName;
  private String recipientPhone;
  private String recipientAddress;
  private String remark;
  private BigDecimal totalAmount;
  private Long createdBy;
  private LocalDateTime createdAt;
  private LocalDateTime completedAt;
}
