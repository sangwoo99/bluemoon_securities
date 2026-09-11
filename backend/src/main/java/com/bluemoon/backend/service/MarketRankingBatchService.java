package com.bluemoon.backend.service;

import com.bluemoon.backend.client.KisClient;
import com.bluemoon.backend.client.KisClient.FluctuationRankItem;
import com.bluemoon.backend.domain.stock.Stock;
import com.bluemoon.backend.domain.stock.TopMover;
import com.bluemoon.backend.mapper.StockMapper;
import com.bluemoon.backend.mapper.TopMoverMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * KIS 등락률 순위(상승률순)를 코스피/코스닥 각각 조회해 상위 10개를 top_movers에 캐싱하는 배치.
 * PriceUpdateBatchService와 마찬가지로 요청 경로가 아닌 이 배치에서만 KIS를 호출한다.
 * 순위에 새로 등장한 종목은 STOCKS에 없을 수 있어 find-or-create로 반영한다.
 * top_movers는 ORDERS 같은 원장이 아니라 "지금 시점의 랭킹"을 보여주는 캐시라, 매 실행마다 비우고 다시 채운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketRankingBatchService {

    private static final int TOP_N = 10;
    /** KIS 모의투자 계좌는 초당 호출 건수 제한이 있어(EGW00201) 코스피/코스닥 호출 사이에 간격을 둔다. */
    private static final long CALL_INTERVAL_MS = 1100;

    private final KisClient kisClient;
    private final StockMapper stockMapper;
    private final TopMoverMapper topMoverMapper;

    /** 배포 직후에도(장 마감/주말 포함) 목록이 비어있지 않도록 앱 기동 시 한 번 실행한다. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        updateTopMovers();
    }

    /** 평일 장중(09~15시) 10분 간격 — 시세 갱신 배치와 동일 주기. */
    @Scheduled(cron = "0 */10 9-15 * * MON-FRI", zone = "Asia/Seoul")
    public void updateTopMovers() {
        List<FluctuationRankItem> kospi = kisClient.getTopFluctuationStocks("0001", TOP_N);
        sleep(CALL_INTERVAL_MS);
        List<FluctuationRankItem> kosdaq = kisClient.getTopFluctuationStocks("1001", TOP_N);

        List<RankedStock> merged = Stream.concat(
                        kospi.stream().map(item -> new RankedStock(item, "KOSPI")),
                        kosdaq.stream().map(item -> new RankedStock(item, "KOSDAQ")))
                .sorted(Comparator.comparing((RankedStock r) -> r.item().changeRatePercent()).reversed())
                .limit(TOP_N)
                .toList();

        if (merged.isEmpty()) {
            log.warn("등락률 순위 조회 결과가 비어있어 top_movers 갱신을 건너뜀");
            return;
        }

        merged.forEach(r -> upsertStock(r.item(), r.market()));

        topMoverMapper.deleteAll();
        for (int i = 0; i < merged.size(); i++) {
            RankedStock r = merged.get(i);
            topMoverMapper.insert(new TopMover(r.item().code(), i + 1, r.item().changeRatePercent()));
        }
    }

    private void upsertStock(FluctuationRankItem item, String market) {
        stockMapper.findByCode(item.code()).ifPresentOrElse(
                stock -> {
                    stock.updatePrice(item.currentPrice());
                    stockMapper.update(stock);
                },
                () -> stockMapper.insert(Stock.seed(item.code(), item.name(), market, item.currentPrice(), estimatePrevClose(item)))
        );
    }

    /** 순위 API는 전일종가를 직접 주지 않아, 현재가와 등락률(%)로 역산한다. */
    private BigDecimal estimatePrevClose(FluctuationRankItem item) {
        BigDecimal rate = item.changeRatePercent().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        return item.currentPrice().divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record RankedStock(FluctuationRankItem item, String market) {
    }
}
