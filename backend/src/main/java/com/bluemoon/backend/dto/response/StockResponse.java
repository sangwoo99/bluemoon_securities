package com.bluemoon.backend.dto.response;

import java.math.BigDecimal;

public record StockResponse(String code, String name, String market, BigDecimal currentPrice, BigDecimal prevClose) {
}
