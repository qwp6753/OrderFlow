package com.orderflow.catalog;

import static com.orderflow.catalog.CatalogRequests.*;

import com.orderflow.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/products")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','WAREHOUSE')")
public class CatalogController {
  private final CatalogService catalog;

  @GetMapping
  public ApiResponse<?> page(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
      @RequestParam(required = false) @Size(max = 100) String name,
      @RequestParam(required = false) @Pattern(regexp = "ON_SALE|OFF_SALE") String status) {
    return ApiResponse.ok(catalog.page(page, size, name, status));
  }

  @GetMapping("/{id}")
  public ApiResponse<?> detail(@PathVariable @Positive Long id) {
    return ApiResponse.ok(catalog.detail(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
  public ApiResponse<?> create(@Valid @RequestBody ProductInput input) {
    return ApiResponse.ok(catalog.create(input));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
  public ApiResponse<?> update(
      @PathVariable @Positive Long id, @Valid @RequestBody ProductInput input) {
    return ApiResponse.ok(catalog.update(id, input));
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
  public ApiResponse<?> status(
      @PathVariable @Positive Long id, @Valid @RequestBody StatusInput input) {
    return ApiResponse.ok(catalog.status(id, input.status()));
  }

  @GetMapping("/{id}/skus")
  public ApiResponse<?> skus(@PathVariable @Positive Long id) {
    return ApiResponse.ok(catalog.listSkus(id));
  }

  @PostMapping("/{id}/skus")
  @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
  public ApiResponse<?> createSku(
      @PathVariable @Positive Long id, @Valid @RequestBody SkuInput input) {
    return ApiResponse.ok(catalog.createSku(id, input));
  }

  @PutMapping("/{id}/skus/{skuId}")
  @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
  public ApiResponse<?> updateSku(
      @PathVariable @Positive Long id,
      @PathVariable @Positive Long skuId,
      @Valid @RequestBody SkuInput input) {
    return ApiResponse.ok(catalog.updateSku(id, skuId, input));
  }
}
