package com.bluemoon.backend.service;

import com.bluemoon.backend.common.exception.ApiException;
import com.bluemoon.backend.common.exception.ErrorCode;
import com.bluemoon.backend.domain.stock.Stock;
import com.bluemoon.backend.dto.response.PricePointResponse;
import com.bluemoon.backend.dto.response.StockResponse;
import com.bluemoon.backend.mapper.PriceSnapshotMapper;
import com.bluemoon.backend.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockMapper stockMapper;
    private final PriceSnapshotMapper priceSnapshotMapper;

    @Transactional(readOnly = true)
    public StockResponse getStock(String code) {
        Stock stock = findStock(code);
        return toResponse(stock);
    }

    @Transactional(readOnly = true)
    public List<StockResponse> getAllStocks() {
        return stockMapper.findAll().stream().map(this::toResponse).toList();
    }

    private StockResponse toResponse(Stock stock) {
        return new StockResponse(stock.getCode(), stock.getName(), stock.getMarket(), stock.getCurrentPrice(), stock.getPrevClose());
    }

    @Transactional(readOnly = true)
    public List<PricePointResponse> getPriceHistory(String code, int days) {
        findStock(code); // 존재 검증

        LocalDate from = LocalDate.now().minusDays(days);
        return priceSnapshotMapper
                .findByStockCodeAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(code, from)
                .stream()
                .map(s -> new PricePointResponse(s.getSnapshotDate(), s.getPrice()))
                .toList();
    }

    Stock findStock(String code) {
        return stockMapper.findByCode(code)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
    }
}
