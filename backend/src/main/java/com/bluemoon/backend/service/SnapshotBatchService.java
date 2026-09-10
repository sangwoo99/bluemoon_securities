package com.bluemoon.backend.service;

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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 장마감 후 하루 1회 시세/자산 스냅샷을 적재해 추이 조회(/api/portfolio/trend, /api/stocks/{code}/price-history)에 사용한다. */
@Service
@RequiredArgsConstructor
public class SnapshotBatchService {

    private final StockMapper stockMapper;
    private final AccountMapper accountMapper;
    private final HoldingMapper holdingMapper;
    private final PriceSnapshotMapper priceSnapshotMapper;
    private final AccountSnapshotMapper accountSnapshotMapper;

    /** 매일 16:00 KST(장마감 후)에 실행. */
    @Scheduled(cron = "0 0 16 * * *", zone = "Asia/Seoul")
    @Transactional
    public void takeDailySnapshots() {
        LocalDate today = LocalDate.now();

        List<Stock> allStocks = stockMapper.findAll();
        for (Stock stock : allStocks) {
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
