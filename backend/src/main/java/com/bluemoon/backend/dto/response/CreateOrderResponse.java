package com.bluemoon.backend.dto.response;

import com.bluemoon.backend.domain.order.OrderStatus;

import java.math.BigDecimal;

/**
 * status가 FILLED면 orderId는 ORDERS.id(체결 완료), PENDING이면 orderId는 PENDING_ORDERS.id(체결 대기)를 가리킨다.
 * price는 체결가(FILLED) 또는 지정가(PENDING).
 */
public record CreateOrderResponse(
        Long orderId,
        OrderStatus status,
        BigDecimal price,
        Long quantity,
        BigDecimal totalAmount,
        BigDecimal cashBalanceAfter
) {
}
