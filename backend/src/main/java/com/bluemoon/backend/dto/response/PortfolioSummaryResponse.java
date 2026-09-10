package com.bluemoon.backend.dto.response;

import java.math.BigDecimal;

public record PortfolioSummaryResponse(
        BigDecimal totalValue,
        BigDecimal totalCost,
        BigDecimal totalGain,
        BigDecimal totalGainRate,
        BigDecimal todayChange,
        BigDecimal todayChangeRate,
        int holdingCount
) {
}
