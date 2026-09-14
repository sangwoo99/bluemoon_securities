package com.bluemoon.backend.service;

import com.bluemoon.backend.client.KisClient;
import com.bluemoon.backend.client.KisClient.FluctuationRankItem;
import com.bluemoon.backend.client.KisClient.VolumeRankItem;
import com.bluemoon.backend.domain.stock.RankType;
import com.bluemoon.backend.domain.stock.Stock;
import com.bluemoon.backend.domain.stock.TopMover;
import com.bluemoon.backend.mapper.StockMapper;
import com.bluemoon.backend.mapper.TopMoverMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * KIS 등락률 순위 / 거래량 순위를 코스피 기준으로 조회해 상위 10개씩 top_movers에 캐싱하는 배치.
 * PriceUpdateBatchService와 마찬가지로 요청 경로가 아닌 이 배치에서만 KIS를 호출한다.
 * 순위에 새로 등장한 종목은 STOCKS에 없을 수 있어 find-or-create로 반영한다.
 * top_movers는 ORDERS 같은 원장이 아니라 "지금 시점의 랭킹"을 보여주는 캐시라, rankType별로 매 실행마다
 * 비우고 다시 채운다 — 두 랭킹은 서로 독립적으로 갱신되므로 상대 rankType의 행은 건드리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketRankingBatchService {

    private static final int TOP_N = 10;
    /** KIS 모의투자 계좌는 초당 호출 건수 제한이 있어(EGW00201) 호출 사이에 간격을 둔다. */
    private static final long CALL_INTERVAL_MS = 1100;

    private final KisClient kisClient;
    private final StockMapper stockMapper;
    private final TopMoverMapper topMoverMapper;

    /**
     * 배포 직후에도(장 마감/주말 포함) 목록이 비어있지 않도록 앱 기동 시 한 번 실행한다.
     * InsightBatchService.onStartup()이 거래량 랭킹(top_movers)을 참조해 "오늘의 추천 종목"을 뽑으므로,
     * 반드시 이 리스너가 먼저 끝난 뒤에 실행돼야 한다 — @Order로 순서를 명시 (기본은 순서 보장이 안 됨).
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void onStartup() {
        updateTopMovers();
    }

    /** 평일 장중(09~15시) 10분 간격 — 시세 갱신 배치와 동일 주기. */
    @Scheduled(cron = "0 */10 9-15 * * MON-FRI", zone = "Asia/Seoul")
    public void updateTopMovers() {
        refreshFluctuationRanking();
        sleep(CALL_INTERVAL_MS);
        refreshVolumeRanking();
    }

    private void refreshFluctuationRanking() {
        List<FluctuationRankItem> kospi = kisClient.getTopFluctuationStocks("0001", TOP_N);

        if (kospi.isEmpty()) {
            log.warn("등락률 순위 조회 결과가 비어있어 갱신을 건너뜀");
            return;
        }

        kospi.forEach(item -> upsertStock(item.code(), item.name(), "KOSPI", item.currentPrice(), item.changeRatePercent()));

        topMoverMapper.deleteByRankType(RankType.FLUCTUATION);
        for (int i = 0; i < kospi.size(); i++) {
            FluctuationRankItem item = kospi.get(i);
            topMoverMapper.insert(TopMover.fluctuation(item.code(), i + 1, item.changeRatePercent()));
        }
    }

    private void refreshVolumeRanking() {
        List<VolumeRankItem> kospi = kisClient.getTopVolumeStocks("0001", TOP_N);

        if (kospi.isEmpty()) {
            log.warn("거래량 순위 조회 결과가 비어있어 갱신을 건너뜀");
            return;
        }

        kospi.forEach(item -> upsertStock(item.code(), item.name(), "KOSPI", item.currentPrice(), item.changeRatePercent()));

        topMoverMapper.deleteByRankType(RankType.VOLUME);
        for (int i = 0; i < kospi.size(); i++) {
            VolumeRankItem item = kospi.get(i);
            topMoverMapper.insert(TopMover.volume(item.code(), i + 1, item.changeRatePercent(), item.volume()));
        }
    }

    private void upsertStock(String code, String name, String market, BigDecimal currentPrice, BigDecimal changeRatePercent) {
        stockMapper.findByCode(code).ifPresentOrElse(
                stock -> {
                    stock.updatePrice(currentPrice);
                    stockMapper.update(stock);
                },
                () -> stockMapper.insert(Stock.seed(code, name, market, currentPrice, estimatePrevClose(currentPrice, changeRatePercent)))
        );
    }

    /** 순위 API들은 전일종가를 직접 주지 않아, 현재가와 등락률(%)로 역산한다. */
    private BigDecimal estimatePrevClose(BigDecimal currentPrice, BigDecimal changeRatePercent) {
        BigDecimal rate = changeRatePercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        return currentPrice.divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
