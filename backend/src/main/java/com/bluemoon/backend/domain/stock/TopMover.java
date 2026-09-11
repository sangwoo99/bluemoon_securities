package com.bluemoon.backend.domain.stock;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * "오늘의 랭킹"(등락률/거래량) 캐시 — 배치가 실행될 때마다 해당 rankType만 비우고 새로 채운다
 * (append-only 아님, ORDERS와는 다른 성격 — 두 랭킹은 서로 독립적으로 갱신되므로 rankType별로만 지운다).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TopMover {

    private Long id;
    private String stockCode;
    private RankType rankType;
    private int rankNo;
    private BigDecimal changeRate;
    private Long volume;
    private LocalDateTime capturedAt;

    private TopMover(String stockCode, RankType rankType, int rankNo, BigDecimal changeRate, Long volume) {
        this.stockCode = stockCode;
        this.rankType = rankType;
        this.rankNo = rankNo;
        this.changeRate = changeRate;
        this.volume = volume;
        this.capturedAt = LocalDateTime.now();
    }

    public static TopMover fluctuation(String stockCode, int rankNo, BigDecimal changeRate) {
        return new TopMover(stockCode, RankType.FLUCTUATION, rankNo, changeRate, null);
    }

    public static TopMover volume(String stockCode, int rankNo, BigDecimal changeRate, long volume) {
        return new TopMover(stockCode, RankType.VOLUME, rankNo, changeRate, volume);
    }
}
