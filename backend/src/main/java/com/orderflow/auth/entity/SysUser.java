package com.orderflow.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
@TableName("sys_user")
public class SysUser {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String username;
  @JsonIgnore private String passwordHash;
  private String displayName;
  private Boolean enabled;
}
