package com.bluemoon.backend.service;

import com.bluemoon.backend.domain.account.Account;
import com.bluemoon.backend.domain.insight.AiInsight;
import com.bluemoon.backend.domain.insight.DailyPick;
import com.bluemoon.backend.domain.stock.RankType;
import com.bluemoon.backend.domain.stock.Stock;
import com.bluemoon.backend.mapper.AccountMapper;
import com.bluemoon.backend.mapper.AiInsightMapper;
import com.bluemoon.backend.mapper.DailyPickMapper;
import com.bluemoon.backend.mapper.HoldingMapper;
import com.bluemoon.backend.mapper.StockMapper;
import com.bluemoon.backend.mapper.TopMoverMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final TopMoverMapper topMoverMapper;
    private final InsightGenerationService insightGenerationService;

    /** 배포 직후에도(다음날 08:00 전까지) AI_INSIGHTS가 비어있지 않도록 앱 기동 시 한 번 실행한다 — MarketRankingBatchService와 동일 패턴. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        generateDailyInsights();
    }

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

        // 계좌가 보유하지 않은 종목 중, 거래량 TOP 랭킹 순으로 훑어 인사이트가 만들어진 첫 종목을 "새로 눈여겨볼 만한 종목"으로 추천한다.
        List<String> volumeRankedCodes = topMoverMapper.findStockCodesByRankTypeOrderByRank(RankType.VOLUME);

        LocalDate today = LocalDate.now();
        for (Account account : accountMapper.findAll()) {
            if (dailyPickMapper.findByAccountIdAndPickDate(account.getId(), today).isPresent()) {
                continue;
            }
            Set<String> heldCodes = holdingMapper.findByAccountIdAndQuantityGreaterThan(account.getId(), 0L).stream()
                    .map(h -> h.getStockCode())
                    .collect(Collectors.toSet());

            volumeRankedCodes.stream()
                    .filter(code -> !heldCodes.contains(code))
                    .map(generatedByStock::get)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .ifPresent(insight -> dailyPickMapper.insert(new DailyPick(account.getId(), insight.getId(), today)));
        }
    }
}
