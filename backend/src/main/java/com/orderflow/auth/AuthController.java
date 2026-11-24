package com.orderflow.auth;

import com.orderflow.auth.mapper.UserMapper;
import com.orderflow.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthenticationManager authenticationManager;
  private final JwtEncoder encoder;
  private final AccountService accounts;
  private final UserMapper users;

  @Value("${app.jwt-ttl}")
  private Duration ttl;

  public record LoginRequest(
      @NotBlank @Size(max = 50) String username, @NotBlank @Size(max = 72) String password) {}

  public record LoginResult(String token, long expiresIn) {}

  public record UserInfo(Long id, String username, String displayName, List<String> roles) {}

  @PostMapping("/login")
  public ApiResponse<LoginResult> login(@Valid @RequestBody LoginRequest request) {
    if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new BusinessException(HttpStatus.BAD_REQUEST, "密码最多72字节");
    try {
      authenticationManager.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(
              request.username(), request.password()));
    } catch (AuthenticationException e) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
    }
    var now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer("orderflow")
            .subject(request.username())
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .build();
    var token =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return ApiResponse.ok(new LoginResult(token, ttl.toSeconds()));
  }

  @GetMapping("/me")
  public ApiResponse<UserInfo> me(Authentication auth) {
    var u = accounts.byUsername(auth.getName());
    return ApiResponse.ok(
        new UserInfo(u.getId(), u.getUsername(), u.getDisplayName(), users.roles(u.getId())));
  }
}
