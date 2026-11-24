package com.orderflow.inventory;

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
@RequestMapping("/api/inventory")
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','WAREHOUSE')")
public class InventoryController {
  private final InventoryService inventory;
  private final AccountService accounts;

  public record Receipt(
      @NotNull @Min(1) Integer quantity, @NotNull @Size(max = 500) String remark) {}

  @GetMapping
  public ApiResponse<?> stocks(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
      @RequestParam(required = false) @Size(max = 100) String keyword) {
    return ApiResponse.ok(inventory.stocks(page, size, keyword));
  }

  @GetMapping("/movements")
  public ApiResponse<?> movements(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
      @RequestParam(required = false) @Positive Long skuId,
      @RequestParam(required = false) @Size(max = 64) String skuCode,
      @RequestParam(required = false) @Pattern(regexp = "RECEIPT|RESERVE|RELEASE|SHIP|RETURN")
          String type,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime to) {
    return ApiResponse.ok(inventory.movements(page, size, skuId, skuCode, type, from, to));
  }

  @PostMapping("/{skuId}/receipts")
  @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
  public ApiResponse<?> receive(
      @PathVariable @Positive Long skuId, @Valid @RequestBody Receipt input, Authentication auth) {
    return ApiResponse.ok(
        inventory.receive(
            skuId, input.quantity(), input.remark(), accounts.userId(auth)));
  }
}
