package com.bluemoon.backend.domain.snapshot;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceSnapshot {

    private Long id;
    private String stockCode;
    private BigDecimal price;
    private LocalDate snapshotDate;

    public PriceSnapshot(String stockCode, BigDecimal price, LocalDate snapshotDate) {
        this.stockCode = stockCode;
        this.price = price;
        this.snapshotDate = snapshotDate;
    }
}
