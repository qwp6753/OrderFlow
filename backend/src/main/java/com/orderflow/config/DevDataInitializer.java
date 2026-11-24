package com.orderflow.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.orderflow.auth.entity.SysUser;
import com.orderflow.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataInitializer implements ApplicationRunner {
  private final UserMapper users;
  private final PasswordEncoder encoder;

  @Value("${app.seed-password}")
  private String password;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (password.isBlank()
        || password.equals("replace-with-your-local-password")
        || password.length() < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException("开发密码必须使用自设值，至少12字符且不超过72字节");
    for (String role : new String[] {"ADMIN", "OPERATOR", "WAREHOUSE"}) {
      String username = role.toLowerCase(java.util.Locale.ROOT);
      if (users.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username)) > 0)
        continue;
      var roleId = users.roleId(role);
      if (roleId == null) throw new IllegalStateException("请先执行数据库初始化脚本");
      var user = new SysUser();
      user.setUsername(username);
      user.setDisplayName(
          switch (role) {
            case "ADMIN" -> "系统管理员";
            case "OPERATOR" -> "运营人员";
            default -> "仓库人员";
          });
      user.setEnabled(true);
      user.setPasswordHash(encoder.encode(password));
      users.insert(user);
      users.assignRole(user.getId(), roleId);
    }
  }
}
