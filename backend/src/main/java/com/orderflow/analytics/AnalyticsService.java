package com.orderflow.analytics;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orderflow.analytics.AnalyticsViews.*;
import com.orderflow.analytics.mapper.AnalyticsMapper;
import com.orderflow.common.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {
  private final AnalyticsMapper mapper;
  private final int threshold;

  public AnalyticsService(AnalyticsMapper mapper, @Value("${app.analytics.low-stock-threshold:10}") int threshold) {
    this.mapper = mapper;
    this.threshold = threshold;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Dashboard dashboard(LocalDate from, LocalDate to, int days) {
    if (from == null && to == null) {
      to = LocalDate.now(ZoneId.of("Asia/Shanghai"));
      from = to.minusDays(days - 1L);
    }
    if (from == null || to == null || from.isAfter(to) || from.getYear() < 1000
        || to.isAfter(LocalDate.of(9999, 12, 30)) || ChronoUnit.DAYS.between(from, to) >= 366) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择有效的起止日期，最多统计366天");
    }

    var start = from.atStartOfDay();
    var end = to.plusDays(1).atStartOfDay();
    var createdByDay = new HashMap<LocalDate, Long>();
    var orderStates = new HashMap<String, Long>();
    for (var row : mapper.createdOrders(start, end)) {
      createdByDay.merge(row.bucketDate(), row.amount(), Long::sum);
      orderStates.merge(row.status(), row.amount(), Long::sum);
    }
    var finished = mapper.completedOrders(start, end);
    var finishedByDay = finished.stream().collect(Collectors.toMap(CompletedDay::bucketDate, Function.identity()));
    var gross = finished.stream().map(CompletedDay::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
    var trend = from.datesUntil(end.toLocalDate()).map(day -> {
      var completed = finishedByDay.get(day);
      return new Trend(day, createdByDay.getOrDefault(day, 0L),
          completed == null ? 0 : completed.amount(), completed == null ? BigDecimal.ZERO : completed.revenue());
    }).toList();
    var returnStates = mapper.createdReturns(start, end).stream()
        .collect(Collectors.toMap(StatusCount::status, StatusCount::amount));
    var completedReturns = mapper.completedReturns(start, end);
    long pending = returnStates.getOrDefault("PENDING_REVIEW", 0L) + returnStates.getOrDefault("PENDING_RECEIPT", 0L);
    return new Dashboard(from, to, counts(orderStates), counts(returnStates), completedReturns, pending,
        gross, gross.subtract(completedReturns.amount()), mapper.inventory(threshold), threshold, trend,
        mapper.topSkus(start, end), mapper.topProducts(start, end));
  }

  public Page<LowSku> lowStock(int page, int size) {
    return mapper.lowStock(new Page<>(page, size), threshold);
  }

  private Counts counts(Map<String, Long> states) {
    return new Counts(states.values().stream().mapToLong(Long::longValue).sum(), states);
  }
}
