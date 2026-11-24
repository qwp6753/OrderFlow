package com.orderflow.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import lombok.Data;

@Data
@TableName("sku")
public class Sku {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long productId;
  private String code;
  private String name;
  private BigDecimal price;
  private String status;
}
