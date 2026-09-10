package com.bluemoon.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PricePointResponse(LocalDate date, BigDecimal price) {
}
