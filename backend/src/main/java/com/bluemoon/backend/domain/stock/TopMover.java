package com.bluemoon.backend.domain.stock;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** "오늘의 상승률 TOP 10" 캐시 — 배치가 실행될 때마다 전체를 비우고 새로 채운다(append-only 아님, ORDERS와는 다른 성격). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TopMover {

    private Long id;
    private String stockCode;
    private int rankNo;
    private BigDecimal changeRate;
    private LocalDateTime capturedAt;

    public TopMover(String stockCode, int rankNo, BigDecimal changeRate) {
        this.stockCode = stockCode;
        this.rankNo = rankNo;
        this.changeRate = changeRate;
        this.capturedAt = LocalDateTime.now();
    }
}
