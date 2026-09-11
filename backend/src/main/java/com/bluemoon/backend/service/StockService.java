package com.bluemoon.backend.service;

import com.bluemoon.backend.common.exception.ApiException;
import com.bluemoon.backend.common.exception.ErrorCode;
import com.bluemoon.backend.domain.stock.Stock;
import com.bluemoon.backend.dto.response.PricePointResponse;
import com.bluemoon.backend.dto.response.StockResponse;
import com.bluemoon.backend.mapper.PriceSnapshotMapper;
import com.bluemoon.backend.mapper.StockMapper;
import com.bluemoon.backend.mapper.TopMoverMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockMapper stockMapper;
    private final PriceSnapshotMapper priceSnapshotMapper;
    private final TopMoverMapper topMoverMapper;

    @Transactional(readOnly = true)
    public StockResponse getStock(String code) {
        Stock stock = findStock(code);
        return toResponse(stock);
    }

    @Transactional(readOnly = true)
    public List<StockResponse> getAllStocks() {
        return stockMapper.findAll().stream().map(this::toResponse).toList();
    }

    /** "오늘의 상승률 TOP 10" — top_movers 캐시만 조회한다(요청 경로에서 KIS를 직접 호출하지 않음). */
    @Transactional(readOnly = true)
    public List<StockResponse> getTopMovers() {
        List<String> codes = topMoverMapper.findStockCodesOrderByRank();
        if (codes.isEmpty()) {
            return List.of();
        }
        Map<String, Stock> byCode = stockMapper.findAllByCodes(codes).stream()
                .collect(Collectors.toMap(Stock::getCode, Function.identity()));
        return codes.stream()
                .map(byCode::get)
                .filter(Objects::nonNull)
                .map(this::toResponse)
                .toList();
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
