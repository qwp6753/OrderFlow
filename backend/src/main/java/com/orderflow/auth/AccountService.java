package com.orderflow.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.orderflow.auth.entity.SysUser;
import com.orderflow.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountService implements UserDetailsService {
  private final UserMapper users;

  @Override
  public UserDetails loadUserByUsername(String username) {
    var u = users.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
    if (u == null) throw new UsernameNotFoundException("用户不存在");
    var authorities =
            users.roles(u.getId()).stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
    return new AuthUser(u.getId(), u.getUsername(), u.getPasswordHash(), u.getEnabled(), authorities);
  }

  public SysUser byUsername(String username) {
    return users.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
  }

  public Long userId(Authentication auth) {
    if (auth.getPrincipal() instanceof AuthUser u) {
      return u.getId();
    }
    return byUsername(auth.getName()).getId();
  }
}