package com.bluemoon.backend.domain.stock;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    private String code;
    private String name;
    private String market;
    private BigDecimal currentPrice;
    private BigDecimal prevClose;
    private LocalDateTime updatedAt;

    public static Stock seed(String code, String name, String market, BigDecimal currentPrice, BigDecimal prevClose) {
        Stock stock = new Stock();
        stock.code = code;
        stock.name = name;
        stock.market = market;
        stock.currentPrice = currentPrice;
        stock.prevClose = prevClose;
        stock.updatedAt = LocalDateTime.now();
        return stock;
    }

    /**
     * 장중 시세 폴링 결과를 반영한다. prevClose는 "직전 폴링 시점 가격"이 아니라 전일 종가를 유지해야 하므로,
     * 반드시 KIS가 내려주는 전일대비율로 역산한 값을 같이 넘겨받는다({@link #estimatePrevClose}).
     * 과거에는 prevClose = 갱신 전 currentPrice로 덮어써서, 10분 폴링마다 "하루 등락률"이 아니라
     * "직전 10분간 등락률"로 수렴하는 버그가 있었다 — 등락률 상위 종목이 목록 화면에서는 0%대로 보이던 원인.
     */
    public void updatePrice(BigDecimal newCurrentPrice, BigDecimal newPrevClose) {
        this.currentPrice = newCurrentPrice;
        this.prevClose = newPrevClose;
        this.updatedAt = LocalDateTime.now();
    }

    /** 순위/시세 API는 전일종가를 직접 주지 않고 등락률(%)만 주는 경우가 있어, 현재가와 등락률로 역산한다. */
    public static BigDecimal estimatePrevClose(BigDecimal currentPrice, BigDecimal changeRatePercent) {
        BigDecimal rate = changeRatePercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        return currentPrice.divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP);
    }
}
