package com.orderflow.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("product")
public class Product {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String name;
  private String description;
  private String status;
}
