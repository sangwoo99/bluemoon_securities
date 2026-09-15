package com.bluemoon.backend.service;

import com.bluemoon.backend.common.KstClock;
import com.bluemoon.backend.domain.account.Account;
import com.bluemoon.backend.domain.holding.Holding;
import com.bluemoon.backend.domain.snapshot.AccountSnapshot;
import com.bluemoon.backend.domain.snapshot.PriceSnapshot;
import com.bluemoon.backend.domain.stock.Stock;
import com.bluemoon.backend.mapper.AccountMapper;
import com.bluemoon.backend.mapper.AccountSnapshotMapper;
import com.bluemoon.backend.mapper.HoldingMapper;
import com.bluemoon.backend.mapper.PriceSnapshotMapper;
import com.bluemoon.backend.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** 장마감 후 하루 1회 시세/자산 스냅샷을 적재해 추이 조회(/api/portfolio/trend, /api/stocks/{code}/price-history)에 사용한다. */
@Service
@RequiredArgsConstructor
public class SnapshotBatchService {

    private final StockMapper stockMapper;
    private final AccountMapper accountMapper;
    private final HoldingMapper holdingMapper;
    private final PriceSnapshotMapper priceSnapshotMapper;
    private final AccountSnapshotMapper accountSnapshotMapper;

    /**
     * 배포 직후(다음 16:00 전까지)에도 "자산 변화 추이" 차트가 완전히 비어있지 않도록 앱 기동 시 한 번 실행한다
     * — MarketRankingBatchService/InsightBatchService와 동일 패턴. account_snapshots는 이 서비스만 쓰므로
     * 오늘 이미 찍혀 있으면 통째로 건너뛰지만, price_snapshots는 PriceHistoryBackfillService도 같은 기동
     * 시점에 오늘자를 채울 수 있어(UNIQUE(stock_code, snapshot_date) 제약과 충돌) 종목별로 따로 확인한다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        takeDailySnapshots();
    }

    /** 매일 16:00 KST(장마감 후)에 실행. */
    @Scheduled(cron = "0 0 16 * * *", zone = "Asia/Seoul")
    @Transactional
    public void takeDailySnapshots() {
        LocalDate today = KstClock.today();

        if (accountSnapshotMapper.existsBySnapshotDate(today)) {
            return;
        }

        List<Stock> allStocks = stockMapper.findAll();
        Set<String> stockCodesWithSnapshotToday = Set.copyOf(priceSnapshotMapper.findStockCodesBySnapshotDate(today));
        for (Stock stock : allStocks) {
            if (stockCodesWithSnapshotToday.contains(stock.getCode())) {
                continue;
            }
            priceSnapshotMapper.insert(new PriceSnapshot(stock.getCode(), stock.getCurrentPrice(), today));
        }

        for (Account account : accountMapper.findAll()) {
            List<Holding> holdings = holdingMapper.findByAccountIdAndQuantityGreaterThan(account.getId(), 0L);
            BigDecimal totalValue = holdings.stream()
                    .map(h -> priceOf(allStocks, h.getStockCode()).multiply(BigDecimal.valueOf(h.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            accountSnapshotMapper.insert(new AccountSnapshot(account.getId(), totalValue, today));
        }
    }

    private BigDecimal priceOf(List<Stock> stocks, String code) {
        return stocks.stream().filter(s -> s.getCode().equals(code)).findFirst()
                .map(Stock::getCurrentPrice)
                .orElse(BigDecimal.ZERO);
    }
}
