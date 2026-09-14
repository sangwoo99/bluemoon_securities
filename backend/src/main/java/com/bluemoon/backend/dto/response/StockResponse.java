package com.bluemoon.backend.dto.response;

import java.math.BigDecimal;

public record StockResponse(String code, String name, String market, BigDecimal currentPrice, BigDecimal prevClose, Long volume) {

    /** 거래량 랭킹 컨텍스트가 아닌 일반 조회(전체 종목, 종목 상세, 관심종목 등)용 — volume 없이 생성. */
    public StockResponse(String code, String name, String market, BigDecimal currentPrice, BigDecimal prevClose) {
        this(code, name, market, currentPrice, prevClose, null);
    }
}
