package com.bluemoon.backend.domain.stock;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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

    public void updatePrice(BigDecimal newCurrentPrice) {
        this.prevClose = this.currentPrice;
        this.currentPrice = newCurrentPrice;
        this.updatedAt = LocalDateTime.now();
    }
}
