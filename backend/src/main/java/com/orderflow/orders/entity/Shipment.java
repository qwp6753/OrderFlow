package com.orderflow.orders.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("shipment")
public class Shipment {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long orderId;
  private String status;
  private String carrier;
  private String trackingNo;
  private Long shippedBy;
  private LocalDateTime shippedAt;
}
