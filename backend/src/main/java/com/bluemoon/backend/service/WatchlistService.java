package com.bluemoon.backend.service;

import com.bluemoon.backend.common.exception.ApiException;
import com.bluemoon.backend.common.exception.ErrorCode;
import com.bluemoon.backend.domain.account.Account;
import com.bluemoon.backend.domain.watchlist.Watchlist;
import com.bluemoon.backend.dto.response.StockResponse;
import com.bluemoon.backend.mapper.AccountMapper;
import com.bluemoon.backend.mapper.StockMapper;
import com.bluemoon.backend.mapper.WatchlistMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final AccountMapper accountMapper;
    private final WatchlistMapper watchlistMapper;
    private final StockMapper stockMapper;

    @Transactional(readOnly = true)
    public List<StockResponse> getMyWatchlist(Long userId) {
        Account account = accountMapper.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));

        List<String> stockCodes = watchlistMapper.findStockCodesByAccountId(account.getId());
        if (stockCodes.isEmpty()) {
            return List.of();
        }
        return stockMapper.findAllByCodes(stockCodes).stream()
                .map(s -> new StockResponse(s.getCode(), s.getName(), s.getMarket(), s.getCurrentPrice(), s.getPrevClose()))
                .toList();
    }

    @Transactional
    public void add(Long userId, String stockCode) {
        Account account = accountMapper.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));
        stockMapper.findByCode(stockCode)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));

        if (!watchlistMapper.existsByAccountIdAndStockCode(account.getId(), stockCode)) {
            watchlistMapper.insert(new Watchlist(account.getId(), stockCode));
        }
    }

    @Transactional
    public void remove(Long userId, String stockCode) {
        Account account = accountMapper.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));
        watchlistMapper.deleteByAccountIdAndStockCode(account.getId(), stockCode);
    }
}
