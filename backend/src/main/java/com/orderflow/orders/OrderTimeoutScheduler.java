package com.orderflow.orders;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.orderflow.common.BusinessException;
import com.orderflow.orders.entity.SalesOrder;
import com.orderflow.orders.mapper.OrderMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final OrderMapper orders;
    private final OrderService orderService;

    @Value("${app.order.payment-timeout:PT30M}")
    private Duration paymentTimeout;

    @Scheduled(fixedDelayString = "${app.order.timeout-scan-interval:60000}")
    public void cancelExpiredOrders() {
        var deadline = LocalDateTime.now(BUSINESS_ZONE).minus(paymentTimeout);
        var expired =
                orders.selectList(
                        Wrappers.<SalesOrder>lambdaQuery()
                                .eq(SalesOrder::getStatus, OrderStatus.PENDING_PAYMENT.name())
                                .lt(SalesOrder::getCreatedAt, deadline)
                                .orderByAsc(SalesOrder::getCreatedAt)
                                .last("LIMIT 50"));
        for (var order : expired) {
            try {
                orderService.cancel(order.getId(), order.getCreatedBy());
                log.info("超时未支付订单已自动关闭: orderId={}, orderNo={}", order.getId(), order.getOrderNo());
            } catch (BusinessException e) {
                // 并发场景下订单可能已被用户支付或手动取消，跳过即可
                log.debug("跳过已变更状态的超时订单: orderId={}, reason={}", order.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("自动关闭超时订单失败: orderId={}", order.getId(), e);
            }
        }
    }
}