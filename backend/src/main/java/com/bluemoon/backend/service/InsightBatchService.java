package com.bluemoon.backend.service;

import com.bluemoon.backend.domain.account.Account;
import com.bluemoon.backend.domain.holding.Holding;
import com.bluemoon.backend.domain.insight.AiInsight;
import com.bluemoon.backend.domain.insight.DailyPick;
import com.bluemoon.backend.domain.stock.Stock;
import com.bluemoon.backend.mapper.AccountMapper;
import com.bluemoon.backend.mapper.AiInsightMapper;
import com.bluemoon.backend.mapper.DailyPickMapper;
import com.bluemoon.backend.mapper.HoldingMapper;
import com.bluemoon.backend.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 하루 1회 뉴스 검색 + LLM 요약으로 AI_INSIGHTS/DAILY_PICKS를 채우는 배치.
 * 요청 경로(/api/insights/*)에서는 이 서비스를 절대 호출하지 않는다 (CLAUDE.md 절대 규칙).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightBatchService {

    private final StockMapper stockMapper;
    private final AccountMapper accountMapper;
    private final HoldingMapper holdingMapper;
    private final AiInsightMapper aiInsightMapper;
    private final DailyPickMapper dailyPickMapper;
    private final InsightGenerationService insightGenerationService;

    /** 매일 08:00 KST에 실행. */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    @Transactional
    public void generateDailyInsights() {
        Map<String, AiInsight> generatedByStock = new HashMap<>();

        for (Stock stock : stockMapper.findAll()) {
            Optional<InsightGenerationService.Result> result = insightGenerationService.generate(stock.getCode(), stock.getName());
            if (result.isEmpty()) {
                log.warn("AI 인사이트 생성 실패 — stockCode={}", stock.getCode());
                continue;
            }
            AiInsight insight = new AiInsight(stock.getCode(), result.get().content(), result.get().sources());
            aiInsightMapper.insert(insight);
            generatedByStock.put(stock.getCode(), insight);
        }

        LocalDate today = LocalDate.now();
        for (Account account : accountMapper.findAll()) {
            if (dailyPickMapper.findByAccountIdAndPickDate(account.getId(), today).isPresent()) {
                continue;
            }
            List<Holding> holdings = holdingMapper.findByAccountIdAndQuantityGreaterThan(account.getId(), 0L);
            holdings.stream()
                    .map(h -> generatedByStock.get(h.getStockCode()))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .ifPresent(insight -> dailyPickMapper.insert(new DailyPick(account.getId(), insight.getId(), today)));
        }
    }
}
