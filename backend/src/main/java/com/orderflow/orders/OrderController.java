package com.orderflow.orders;

import com.orderflow.auth.AccountService;
import com.orderflow.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','WAREHOUSE')")
public class OrderController {
  private final OrderService orders;
  private final AccountService accounts;

  @GetMapping
  public ApiResponse<?> page(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
      @RequestParam(required = false) @Size(max = 40) String orderNo,
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime to) {
    return ApiResponse.ok(orders.page(page, size, orderNo, status, from, to));
  }

  @GetMapping("/{id}")
  public ApiResponse<?> detail(@PathVariable @Positive Long id) {
    return ApiResponse.ok(orders.detail(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
  public ApiResponse<?> create(
      @Valid @RequestBody OrderRequests.CreateOrder input, Authentication auth) {
    return ApiResponse.ok(orders.create(input, accounts.userId(auth)));
  }

  @PostMapping("/{id}/simulate-payment")
  @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
  public ApiResponse<?> pay(@PathVariable @Positive Long id) {
    return ApiResponse.ok(orders.pay(id));
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
  public ApiResponse<?> cancel(@PathVariable @Positive Long id, Authentication auth) {
    return ApiResponse.ok(orders.cancel(id, accounts.userId(auth)));
  }

  @PostMapping("/{id}/ship")
  @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
  public ApiResponse<?> ship(
      @PathVariable @Positive Long id,
      @Valid @RequestBody OrderRequests.ShipOrder input,
      Authentication auth) {
    return ApiResponse.ok(orders.ship(id, input, accounts.userId(auth)));
  }

  @PostMapping("/{id}/complete")
  @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
  public ApiResponse<?> complete(@PathVariable @Positive Long id) {
    return ApiResponse.ok(orders.complete(id));
  }
}
