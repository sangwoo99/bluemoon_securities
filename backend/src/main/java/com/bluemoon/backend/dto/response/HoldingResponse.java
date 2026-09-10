package com.bluemoon.backend.dto.response;

import java.math.BigDecimal;

public record HoldingResponse(
        String stockCode,
        String stockName,
        Long quantity,
        BigDecimal avgPrice,
        BigDecimal currentPrice,
        BigDecimal evalValue,
        BigDecimal evalGain,
        BigDecimal evalGainRate
) {
}
