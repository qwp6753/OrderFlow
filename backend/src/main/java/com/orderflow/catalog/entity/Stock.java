package com.orderflow.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("stock")
public class Stock {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long skuId;
  private Integer quantity;
  private Integer reservedQuantity;
}
