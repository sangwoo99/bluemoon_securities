package com.bluemoon.backend.service;

import com.bluemoon.backend.common.exception.ApiException;
import com.bluemoon.backend.common.exception.ErrorCode;
import com.bluemoon.backend.domain.account.Account;
import com.bluemoon.backend.domain.holding.Holding;
import com.bluemoon.backend.domain.stock.Stock;
import com.bluemoon.backend.dto.response.HoldingResponse;
import com.bluemoon.backend.mapper.AccountMapper;
import com.bluemoon.backend.mapper.HoldingMapper;
import com.bluemoon.backend.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HoldingService {

    private final AccountMapper accountMapper;
    private final HoldingMapper holdingMapper;
    private final StockMapper stockMapper;

    @Transactional(readOnly = true)
    public List<HoldingResponse> getHoldings(Long userId) {
        Account account = accountMapper.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));

        return holdingMapper.findByAccountIdAndQuantityGreaterThan(account.getId(), 0L).stream()
                .map(this::toResponse)
                .toList();
    }

    private HoldingResponse toResponse(Holding holding) {
        Stock stock = stockMapper.findByCode(holding.getStockCode())
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));

        BigDecimal quantity = BigDecimal.valueOf(holding.getQuantity());
        BigDecimal evalValue = stock.getCurrentPrice().multiply(quantity);
        BigDecimal cost = holding.getAvgPrice().multiply(quantity);
        BigDecimal evalGain = evalValue.subtract(cost);
        BigDecimal evalGainRate = cost.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : evalGain.divide(cost, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        return new HoldingResponse(
                stock.getCode(),
                stock.getName(),
                holding.getQuantity(),
                holding.getAvgPrice(),
                stock.getCurrentPrice(),
                evalValue,
                evalGain,
                evalGainRate.setScale(2, RoundingMode.HALF_UP)
        );
    }
}
