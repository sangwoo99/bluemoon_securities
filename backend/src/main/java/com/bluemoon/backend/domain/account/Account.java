package com.bluemoon.backend.domain.account;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    private Long id;
    private Long userId;
    private BigDecimal cashBalance;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Account(Long userId, BigDecimal seedCashBalance) {
        this.userId = userId;
        this.cashBalance = seedCashBalance;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void debit(BigDecimal amount) {
        if (cashBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("cash balance insufficient");
        }
        this.cashBalance = this.cashBalance.subtract(amount);
        this.updatedAt = LocalDateTime.now();
    }

    public void credit(BigDecimal amount) {
        this.cashBalance = this.cashBalance.add(amount);
        this.updatedAt = LocalDateTime.now();
    }

    public boolean hasEnoughBalance(BigDecimal amount) {
        return cashBalance.compareTo(amount) >= 0;
    }
}
