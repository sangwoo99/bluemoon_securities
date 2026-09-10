package com.bluemoon.backend.dto.response;

import com.bluemoon.backend.domain.order.OrderSide;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderHistoryResponse(
        Long orderId,
        String stockCode,
        String stockName,
        OrderSide side,
        Long quantity,
        BigDecimal price,
        LocalDateTime orderedAt
) {
}
