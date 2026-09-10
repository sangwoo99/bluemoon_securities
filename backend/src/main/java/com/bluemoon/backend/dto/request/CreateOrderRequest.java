package com.bluemoon.backend.dto.request;

import com.bluemoon.backend.domain.order.OrderSide;
import com.bluemoon.backend.domain.order.OrderType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotNull String stockCode,
        @NotNull OrderSide side,
        @NotNull OrderType orderType,
        @NotNull @Min(1) Long quantity,
        BigDecimal limitPrice
) {
}
