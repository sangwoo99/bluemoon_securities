package com.bluemoon.backend.service;

import com.bluemoon.backend.client.KisClient;
import com.bluemoon.backend.common.exception.ApiException;
import com.bluemoon.backend.common.exception.ErrorCode;
import com.bluemoon.backend.domain.stock.RankType;
import com.bluemoon.backend.domain.stock.Stock;
import com.bluemoon.backend.domain.stock.TopMover;
import com.bluemoon.backend.dto.response.IntradayPricePointResponse;
import com.bluemoon.backend.dto.response.PricePointResponse;
import com.bluemoon.backend.dto.response.StockResponse;
import com.bluemoon.backend.mapper.PriceSnapshotMapper;
import com.bluemoon.backend.mapper.StockMapper;
import com.bluemoon.backend.mapper.TopMoverMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    /** "당일" 탭을 열 때마다 KIS를 부르지 않도록, 종목당 30분에 한 번만 실제로 조회하고 그 사이엔 Redis 캐시를 재사용한다. */
    private static final Duration INTRADAY_CACHE_TTL = Duration.ofMinutes(30);

    private final StockMapper stockMapper;
    private final PriceSnapshotMapper priceSnapshotMapper;
    private final TopMoverMapper topMoverMapper;
    private final KisClient kisClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public StockResponse getStock(String code) {
        Stock stock = findStock(code);
        return toResponse(stock);
    }

    @Transactional(readOnly = true)
    public List<StockResponse> getAllStocks() {
        return stockMapper.findAll().stream().map(this::toResponse).toList();
    }

    /** "오늘의 랭킹 TOP 10"(등락률/거래량) — top_movers 캐시만 조회한다(요청 경로에서 KIS를 직접 호출하지 않음). */
    @Transactional(readOnly = true)
    public List<StockResponse> getTopMovers(String rankType) {
        RankType type;
        try {
            type = RankType.valueOf(rankType);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "type은 FLUCTUATION 또는 VOLUME이어야 합니다.");
        }

        List<TopMover> rankedMovers = topMoverMapper.findByRankTypeOrderByRank(type);
        if (rankedMovers.isEmpty()) {
            return List.of();
        }
        List<String> codes = rankedMovers.stream().map(TopMover::getStockCode).toList();
        Map<String, Stock> byCode = stockMapper.findAllByCodes(codes).stream()
                .collect(Collectors.toMap(Stock::getCode, Function.identity()));
        return rankedMovers.stream()
                .filter(m -> byCode.containsKey(m.getStockCode()))
                .map(m -> toResponse(byCode.get(m.getStockCode()), m.getVolume()))
                .toList();
    }

    private StockResponse toResponse(Stock stock) {
        return new StockResponse(stock.getCode(), stock.getName(), stock.getMarket(), stock.getCurrentPrice(), stock.getPrevClose());
    }

    private StockResponse toResponse(Stock stock, Long volume) {
        return new StockResponse(stock.getCode(), stock.getName(), stock.getMarket(), stock.getCurrentPrice(), stock.getPrevClose(), volume);
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

    /**
     * 당일 분봉 시세. price-history(price_snapshots, 일별 배치 캐시)와 달리 DB 캐시가 없어 KIS를 직접 호출해야
     * 하지만, 요청마다 매번 부르면 트래픽이 늘 때 KIS 호출 빈도 제한에 걸리기 쉬워 Redis에 종목당 30분 캐시를 둔다.
     * 즉 같은 종목을 여러 사람이 30분 안에 반복 조회해도 KIS는 최대 30분에 한 번만 불린다.
     */
    public List<IntradayPricePointResponse> getIntradayPriceHistory(String code) {
        findStock(code); // 존재 검증

        String cacheKey = "intraday:" + code;
        List<IntradayPricePointResponse> cached = readIntradayCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<IntradayPricePointResponse> fresh = kisClient.getIntradayPriceHistory(code).stream()
                .map(p -> new IntradayPricePointResponse(p.time(), p.price()))
                .toList();
        writeIntradayCache(cacheKey, fresh);
        return fresh;
    }

    /** Redis 장애/직렬화 문제로 캐시를 못 읽어도 기능 자체는 죽지 않도록(그냥 매번 KIS를 다시 부르는 셈) null만 반환한다. */
    private List<IntradayPricePointResponse> readIntradayCache(String cacheKey) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null) {
                return null;
            }
            return objectMapper.readValue(cached, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, IntradayPricePointResponse.class));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("당일 분봉 캐시 조회 실패 — key={}, error={}", cacheKey, e.getMessage());
            return null;
        }
    }

    private void writeIntradayCache(String cacheKey, List<IntradayPricePointResponse> value) {
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(value), INTRADAY_CACHE_TTL);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("당일 분봉 캐시 저장 실패 — key={}, error={}", cacheKey, e.getMessage());
        }
    }

    Stock findStock(String code) {
        return stockMapper.findByCode(code)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
    }
}
