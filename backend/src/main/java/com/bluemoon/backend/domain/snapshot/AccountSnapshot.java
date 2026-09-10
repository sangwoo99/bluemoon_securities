package com.bluemoon.backend.domain.snapshot;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountSnapshot {

    private Long id;
    private Long accountId;
    private BigDecimal totalValue;
    private LocalDate snapshotDate;

    public AccountSnapshot(Long accountId, BigDecimal totalValue, LocalDate snapshotDate) {
        this.accountId = accountId;
        this.totalValue = totalValue;
        this.snapshotDate = snapshotDate;
    }
}
