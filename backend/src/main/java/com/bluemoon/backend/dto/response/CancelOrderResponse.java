package com.bluemoon.backend.dto.response;

import java.math.BigDecimal;

public record CancelOrderResponse(
        Long orderId,
        BigDecimal cashBalanceAfter
) {
}
