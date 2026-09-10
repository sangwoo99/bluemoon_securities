package com.bluemoon.backend.dto.response;

import java.math.BigDecimal;

public record CreateOrderResponse(
        Long orderId,
        BigDecimal filledPrice,
        Long filledQuantity,
        BigDecimal totalAmount,
        BigDecimal cashBalanceAfter
) {
}
