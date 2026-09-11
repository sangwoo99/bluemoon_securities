package com.bluemoon.backend.domain.watchlist;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Watchlist {

    private Long id;
    private Long accountId;
    private String stockCode;
    private LocalDateTime createdAt;

    public Watchlist(Long accountId, String stockCode) {
        this.accountId = accountId;
        this.stockCode = stockCode;
        this.createdAt = LocalDateTime.now();
    }
}
