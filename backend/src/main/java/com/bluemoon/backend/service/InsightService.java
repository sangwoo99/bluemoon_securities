package com.bluemoon.backend.service;

import com.bluemoon.backend.common.exception.ApiException;
import com.bluemoon.backend.common.exception.ErrorCode;
import com.bluemoon.backend.domain.account.Account;
import com.bluemoon.backend.domain.insight.AiInsight;
import com.bluemoon.backend.domain.insight.DailyPick;
import com.bluemoon.backend.domain.stock.Stock;
import com.bluemoon.backend.dto.response.InsightResponse;
import com.bluemoon.backend.mapper.AccountMapper;
import com.bluemoon.backend.mapper.AiInsightMapper;
import com.bluemoon.backend.mapper.DailyPickMapper;
import com.bluemoon.backend.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 항상 AI_INSIGHTS/DAILY_PICKS 테이블을 조회만 한다. 생성은 {@link InsightBatchService}에서 하루 1회만 수행 (CLAUDE.md 절대 규칙).
 */
@Service
@RequiredArgsConstructor
public class InsightService {

    private final AccountMapper accountMapper;
    private final DailyPickMapper dailyPickMapper;
    private final AiInsightMapper aiInsightMapper;
    private final StockMapper stockMapper;

    @Transactional(readOnly = true)
    public InsightResponse getToday(Long userId) {
        Account account = accountMapper.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));

        DailyPick pick = dailyPickMapper.findByAccountIdAndPickDate(account.getId(), LocalDate.now()).orElse(null);
        if (pick == null) {
            return null;
        }

        AiInsight insight = aiInsightMapper.findById(pick.getInsightId()).orElse(null);
        if (insight == null) {
            return null;
        }

        return toResponse(insight);
    }

    @Transactional(readOnly = true)
    public InsightResponse getForStock(String stockCode) {
        return aiInsightMapper.findFirstByStockCodeOrderByGeneratedAtDesc(stockCode)
                .map(this::toResponse)
                .orElse(null);
    }

    private InsightResponse toResponse(AiInsight insight) {
        Stock stock = stockMapper.findByCode(insight.getStockCode()).orElse(null);
        var sources = insight.getSources().stream()
                .map(s -> new InsightResponse.InsightSourceResponse(s.name(), s.date()))
                .toList();

        return new InsightResponse(
                insight.getStockCode(),
                stock != null ? stock.getName() : null,
                insight.getContent(),
                sources,
                insight.getGeneratedAt()
        );
    }
}
