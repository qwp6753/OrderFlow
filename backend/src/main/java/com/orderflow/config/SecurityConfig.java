package com.orderflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.orderflow.auth.AccountService;
import com.orderflow.common.ApiResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(AccountService accounts, PasswordEncoder encoder) {
    var provider = new DaoAuthenticationProvider(accounts);
    provider.setPasswordEncoder(encoder);
    return new ProviderManager(provider);
  }

  @Bean
  SecretKeySpec jwtKey(@Value("${app.jwt-secret}") String secret) {
    var bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32 || secret.isBlank() || secret.equals("replace-with-at-least-32-random-bytes")) {
      throw new IllegalArgumentException("JWT_SECRET 必须使用至少32字节的私密随机值");
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKeySpec key) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKeySpec key) {
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("orderflow"));
    return decoder;
  }

  @Bean
  CorsConfigurationSource cors(@Value("${app.cors-origins}") List<String> origins) {
    var c = new CorsConfiguration();
    c.setAllowedOrigins(origins);
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return source;
  }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, AccountService accounts, ObjectMapper json)
      throws Exception {
    http.csrf(c -> c.disable())
        .cors(c -> {})
        .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.authorizeHttpRequests(
        c ->
            c.requestMatchers(
                    "/api/auth/login", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                .permitAll()
                .anyRequest()
                .authenticated());
    http.oauth2ResourceServer(
        c ->
            c.jwt(
                    j ->
                        j.jwtAuthenticationConverter(
                            token -> {
                              var user = accounts.loadUserByUsername(token.getSubject());
                              if (!user.isEnabled()) throw new BadCredentialsException("账号已禁用");
                              return new org.springframework.security.authentication
                                  .UsernamePasswordAuthenticationToken(
                                  user, null, user.getAuthorities());
                            }))
                .authenticationEntryPoint(
                    (req, res, e) -> {
                      res.setStatus(401);
                      res.setContentType("application/json;charset=UTF-8");
                      json.writeValue(
                          res.getWriter(), ApiResponse.error("UNAUTHORIZED", "请登录或重新登录"));
                    }));
    http.exceptionHandling(
        c ->
            c.authenticationEntryPoint(
                    (req, res, e) -> {
                      res.setStatus(401);
                      res.setContentType("application/json;charset=UTF-8");
                      json.writeValue(
                          res.getWriter(), ApiResponse.error("UNAUTHORIZED", "请登录或重新登录"));
                    })
                .accessDeniedHandler(
                    (req, res, e) -> {
                      res.setStatus(403);
                      res.setContentType("application/json;charset=UTF-8");
                      json.writeValue(res.getWriter(), ApiResponse.error("FORBIDDEN", "无权执行此操作"));
                    }));
    return http.build();
  }
}
