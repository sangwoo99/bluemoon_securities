package com.bluemoon.backend.service;

import com.bluemoon.backend.client.KisClient;
import com.bluemoon.backend.domain.snapshot.PriceSnapshot;
import com.bluemoon.backend.domain.stock.Stock;
import com.bluemoon.backend.mapper.PriceSnapshotMapper;
import com.bluemoon.backend.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 앱 기동 시 한 번, KIS 기간별시세 API로 최근 1년 시세를 PRICE_SNAPSHOTS에 백필한다.
 * SnapshotBatchService(매일 16:00, 하루 1건)가 쌓기 전에도 "시세 추이" 차트(1주/1개월/3개월/1년)가 바로
 * 보이도록 과거분을 채워준다. 최근 구간은 일봉으로, 오래된 구간은 주봉으로 받아 KIS의 100건 응답 제약을 피한다.
 * 종목별로 이미 충분히 과거까지 쌓여있으면 건너뛰어, 재기동마다 KIS를 다시 호출하지 않는다.
 * PriceUpdateBatchService/MarketRankingBatchService와 마찬가지로 요청 경로가 아닌 이 배치에서만 KIS를 호출한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceHistoryBackfillService {

    /** 최근 이 기간은 일봉으로 받는다 — "1주/1개월/3개월" 화면이 이 구간만으로 채워진다. */
    private static final int DAILY_RANGE_DAYS = 95;
    /** 전체 백필 기간(1년) — 나머지 구간은 주봉으로 받는다. */
    private static final int TOTAL_BACKFILL_DAYS = 365;
    /** KIS 모의투자 계좌는 초당 호출 건수 제한이 있어(EGW00201) 호출 사이에 간격을 둔다. */
    private static final long CALL_INTERVAL_MS = 1100;

    private final KisClient kisClient;
    private final StockMapper stockMapper;
    private final PriceSnapshotMapper priceSnapshotMapper;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillPriceHistory() {
        // 같은 기동 시점에 MarketRankingBatchService도 KIS를 호출해 초당 호출 제한(EGW00201)에 걸리기 쉬워, 살짝 늦춰 시작한다.
        sleep(CALL_INTERVAL_MS * 2);

        LocalDate today = LocalDate.now();
        LocalDate dailyFrom = today.minusDays(DAILY_RANGE_DAYS);
        LocalDate totalFrom = today.minusDays(TOTAL_BACKFILL_DAYS);

        for (Stock stock : stockMapper.findAll()) {
            Set<LocalDate> existingDates = priceSnapshotMapper
                    .findByStockCodeAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(stock.getCode(), totalFrom)
                    .stream()
                    .map(PriceSnapshot::getSnapshotDate)
                    .collect(Collectors.toSet());

            LocalDate oldestExisting = existingDates.stream().min(Comparator.naturalOrder()).orElse(null);
            if (oldestExisting != null && !oldestExisting.isAfter(totalFrom.plusDays(3))) {
                continue;
            }

            List<KisClient.DailyPriceItem> history = new ArrayList<>();
            history.addAll(kisClient.getPriceHistory(stock.getCode(), dailyFrom, today, "D"));
            sleep(CALL_INTERVAL_MS);
            history.addAll(kisClient.getPriceHistory(stock.getCode(), totalFrom, dailyFrom.minusDays(1), "W"));
            sleep(CALL_INTERVAL_MS);

            if (history.isEmpty()) {
                log.warn("KIS 기간별시세 백필 실패 — stockCode={}", stock.getCode());
            }
            history.stream()
                    .filter(item -> !existingDates.contains(item.date()))
                    .forEach(item -> priceSnapshotMapper.insert(new PriceSnapshot(stock.getCode(), item.closePrice(), item.date())));
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
