package com.bluemoon.backend.dto.response;

import com.bluemoon.backend.domain.order.OrderSide;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TradeResponse(Long orderId, OrderSide side, Long quantity, BigDecimal price, LocalDateTime orderedAt) {
}
