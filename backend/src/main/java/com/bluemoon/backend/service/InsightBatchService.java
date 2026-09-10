package com.bluemoon.backend.service;

import com.bluemoon.backend.client.RagServiceClient;
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

/**
 * 하루 1회 Python RAG 서비스를 호출해 AI_INSIGHTS/DAILY_PICKS를 채우는 배치.
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
    private final RagServiceClient ragServiceClient;

    /** 매일 08:00 KST에 실행. */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    @Transactional
    public void generateDailyInsights() {
        Map<String, AiInsight> generatedByStock = new HashMap<>();

        for (Stock stock : stockMapper.findAll()) {
            RagServiceClient.GenerateInsightResponse response = ragServiceClient.generateInsight(stock.getCode(), stock.getName());
            if (response == null) {
                log.warn("RAG 서비스 응답 없음 — stockCode={}", stock.getCode());
                continue;
            }
            List<AiInsight.InsightSource> sources = response.sources().stream()
                    .map(s -> new AiInsight.InsightSource(s.name(), s.date()))
                    .toList();
            AiInsight insight = new AiInsight(stock.getCode(), response.content(), sources);
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
