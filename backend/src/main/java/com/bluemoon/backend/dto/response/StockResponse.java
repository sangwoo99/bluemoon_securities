package com.bluemoon.backend.dto.response;

import java.math.BigDecimal;

public record StockResponse(String code, String name, String market, BigDecimal currentPrice, BigDecimal prevClose, Long volume, boolean hasInsight) {

    /** 거래량 랭킹 컨텍스트가 아닌 일반 조회(종목 상세, 관심종목 등)용 — volume/hasInsight 없이 생성. */
    public StockResponse(String code, String name, String market, BigDecimal currentPrice, BigDecimal prevClose) {
        this(code, name, market, currentPrice, prevClose, null, false);
    }

    /** 거래량 랭킹(top-movers) 응답용 — hasInsight 없이 생성. */
    public StockResponse(String code, String name, String market, BigDecimal currentPrice, BigDecimal prevClose, Long volume) {
        this(code, name, market, currentPrice, prevClose, volume, false);
    }
}
