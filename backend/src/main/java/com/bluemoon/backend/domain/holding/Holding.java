package com.bluemoon.backend.domain.holding;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holding {

    private Long id;
    private Long accountId;
    private String stockCode;
    private Long quantity;
    private BigDecimal avgPrice;
    private LocalDateTime updatedAt;

    public Holding(Long accountId, String stockCode) {
        this.accountId = accountId;
        this.stockCode = stockCode;
        this.quantity = 0L;
        this.avgPrice = BigDecimal.ZERO;
        this.updatedAt = LocalDateTime.now();
    }

    /** 매수 체결 반영 — 평단가를 가중평균으로 재계산한다. */
    public void applyBuy(long filledQuantity, BigDecimal filledPrice) {
        BigDecimal existingCost = this.avgPrice.multiply(BigDecimal.valueOf(this.quantity));
        BigDecimal newCost = filledPrice.multiply(BigDecimal.valueOf(filledQuantity));
        long newQuantity = this.quantity + filledQuantity;

        this.avgPrice = existingCost.add(newCost)
                .divide(BigDecimal.valueOf(newQuantity), 2, java.math.RoundingMode.HALF_UP);
        this.quantity = newQuantity;
        this.updatedAt = LocalDateTime.now();
    }

    /** 매도 체결 반영 — 평단가는 유지, 수량만 차감한다. */
    public void applySell(long filledQuantity) {
        if (this.quantity < filledQuantity) {
            throw new IllegalStateException("insufficient holding quantity");
        }
        this.quantity -= filledQuantity;
        this.updatedAt = LocalDateTime.now();
        if (this.quantity == 0) {
            this.avgPrice = BigDecimal.ZERO;
        }
    }

    public boolean hasEnoughQuantity(long requestedQuantity) {
        return this.quantity >= requestedQuantity;
    }
}
