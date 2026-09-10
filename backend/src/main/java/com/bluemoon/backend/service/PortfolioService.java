package com.bluemoon.backend.service;

import com.bluemoon.backend.common.exception.ApiException;
import com.bluemoon.backend.common.exception.ErrorCode;
import com.bluemoon.backend.domain.account.Account;
import com.bluemoon.backend.domain.holding.Holding;
import com.bluemoon.backend.domain.snapshot.AccountSnapshot;
import com.bluemoon.backend.domain.stock.Stock;
import com.bluemoon.backend.dto.response.PortfolioSummaryResponse;
import com.bluemoon.backend.dto.response.TrendPointResponse;
import com.bluemoon.backend.mapper.AccountMapper;
import com.bluemoon.backend.mapper.AccountSnapshotMapper;
import com.bluemoon.backend.mapper.HoldingMapper;
import com.bluemoon.backend.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final AccountMapper accountMapper;
    private final HoldingMapper holdingMapper;
    private final StockMapper stockMapper;
    private final AccountSnapshotMapper accountSnapshotMapper;

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse getSummary(Long userId) {
        Account account = accountMapper.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));

        List<Holding> holdings = holdingMapper.findByAccountIdAndQuantityGreaterThan(account.getId(), 0L);
        List<String> stockCodes = holdings.stream().map(Holding::getStockCode).toList();
        Map<String, Stock> stocksByCode = stockCodes.isEmpty()
                ? Map.of()
                : stockMapper.findAllByCodes(stockCodes)
                        .stream()
                        .collect(java.util.stream.Collectors.toMap(Stock::getCode, Function.identity()));

        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (Holding holding : holdings) {
            Stock stock = stocksByCode.get(holding.getStockCode());
            BigDecimal quantity = BigDecimal.valueOf(holding.getQuantity());
            totalValue = totalValue.add(stock.getCurrentPrice().multiply(quantity));
            totalCost = totalCost.add(holding.getAvgPrice().multiply(quantity));
        }

        BigDecimal totalGain = totalValue.subtract(totalCost);
        BigDecimal totalGainRate = percentageOf(totalGain, totalCost);

        BigDecimal yesterdayValue = accountSnapshotMapper
                .findByAccountIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(account.getId(), LocalDate.now().minusDays(7))
                .stream()
                .filter(s -> s.getSnapshotDate().isBefore(LocalDate.now()))
                .reduce((first, second) -> second)
                .map(AccountSnapshot::getTotalValue)
                .orElse(null);

        BigDecimal todayChange = yesterdayValue == null ? BigDecimal.ZERO : totalValue.subtract(yesterdayValue);
        BigDecimal todayChangeRate = yesterdayValue == null ? BigDecimal.ZERO : percentageOf(todayChange, yesterdayValue);

        return new PortfolioSummaryResponse(
                totalValue,
                totalCost,
                totalGain,
                totalGainRate,
                todayChange,
                todayChangeRate,
                holdings.size()
        );
    }

    @Transactional(readOnly = true)
    public List<TrendPointResponse> getTrend(Long userId, String period) {
        Account account = accountMapper.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));

        LocalDate from = LocalDate.now().minusDays(periodToDays(period));

        return accountSnapshotMapper
                .findByAccountIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(account.getId(), from)
                .stream()
                .map(s -> new TrendPointResponse(s.getSnapshotDate(), s.getTotalValue()))
                .toList();
    }

    private long periodToDays(String period) {
        return switch (period) {
            case "1W" -> 7;
            case "3M" -> 90;
            case "1Y" -> 365;
            default -> 30; // 1M
        };
    }

    private BigDecimal percentageOf(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }
}
