package com.orderflow.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orderflow.auth.entity.SysUser;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface UserMapper extends BaseMapper<SysUser> {
  @Select(
      "SELECT r.code FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id WHERE"
          + " ur.user_id=#{userId}")
  List<String> roles(Long userId);

  @Select("SELECT id FROM sys_role WHERE code=#{code}")
  Long roleId(String code);

  @Insert("INSERT INTO sys_user_role(user_id,role_id) VALUES(#{userId},#{roleId})")
  void assignRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
