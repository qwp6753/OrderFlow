package com.orderflow.auth;

import com.orderflow.auth.entity.SysUser;
import com.orderflow.auth.mapper.UserMapper;
import com.orderflow.common.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
  private final UserMapper users;

  @GetMapping("/users")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<List<SysUser>> users() {
    return ApiResponse.ok(users.selectList(null));
  }
}
