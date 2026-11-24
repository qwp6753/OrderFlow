package com.orderflow.returns.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import lombok.Data;

@Data
@TableName("return_item")
public class ReturnItem {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long returnId;
  private Long orderItemId;
  private Integer quantity;
  private BigDecimal refundAmount;
}