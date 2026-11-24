package com.orderflow.returns;

import com.orderflow.auth.AccountService;
import com.orderflow.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/returns")
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','WAREHOUSE')")
public class ReturnController {
    private final ReturnService returns;
    private final AccountService accounts;

    public record Create(@NotNull @Positive Long orderItemId, @NotNull @Positive Integer quantity,
                         @NotBlank @Size(max=500) String reason) {}
    public record Rejection(@NotBlank @Size(max=500) String reason) {}
    public record Receipt(@Size(max=500) String remark) {}

    @GetMapping
    public ApiResponse<?> page(@RequestParam(defaultValue="1") @Min(1) int page,
                               @RequestParam(defaultValue="10") @Min(1) @Max(100) int size,
                               @RequestParam(required=false) @Positive Long orderId,
                               @RequestParam(required=false) @Pattern(regexp="PENDING_REVIEW|REJECTED|PENDING_RECEIPT|COMPLETED") String status) {
        return ApiResponse.ok(returns.page(page,size,orderId,status));
    }

    @GetMapping("/{id}")
    public ApiResponse<?> detail(@PathVariable @Positive Long id) {
        return ApiResponse.ok(returns.detail(id));
    }

    @GetMapping("/availability")
    public ApiResponse<?> availability(@RequestParam @Positive Long orderId) {
        return ApiResponse.ok(returns.availability(orderId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<?> create(@Valid @RequestBody Create input, Authentication auth) {
        return ApiResponse.ok(returns.create(input.orderItemId(),input.quantity(),input.reason(),actor(auth)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<?> approve(@PathVariable @Positive Long id, Authentication auth) {
        returns.review(id,null,actor(auth));
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<?> reject(@PathVariable @Positive Long id, @Valid @RequestBody Rejection input, Authentication auth) {
        returns.review(id,input.reason(),actor(auth));
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
    public ApiResponse<?> receive(@PathVariable @Positive Long id, @Valid @RequestBody Receipt input, Authentication auth) {
        returns.receive(id,input.remark() == null ? "" : input.remark(),actor(auth));
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/note")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
    public ApiResponse<?> note(@PathVariable @Positive Long id, @Valid @RequestBody Rejection input, Authentication auth) {
        returns.note(id,input.reason(),actor(auth));
        return ApiResponse.ok(null);
    }

    private Long actor(Authentication auth) {
        return accounts.userId(auth);
    }
}