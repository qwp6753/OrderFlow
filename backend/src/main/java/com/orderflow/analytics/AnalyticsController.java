package com.orderflow.analytics;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orderflow.analytics.AnalyticsViews.Dashboard;
import com.orderflow.analytics.AnalyticsViews.LowSku;
import com.orderflow.common.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
public class AnalyticsController {
  private final AnalyticsService analytics;

  @GetMapping("/dashboard")
  public ApiResponse<Dashboard> dashboard(
      @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue="7") @Min(1) @Max(366) int days) {
    return ApiResponse.ok(analytics.dashboard(from, to, days));
  }

  @GetMapping("/low-stock")
  public ApiResponse<Page<LowSku>> lowStock(@RequestParam(defaultValue="1") @Min(1) int page,
      @RequestParam(defaultValue="10") @Min(1) @Max(100) int size) {
    return ApiResponse.ok(analytics.lowStock(page, size));
  }
}
