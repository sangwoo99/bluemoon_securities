package com.bluemoon.backend.service;

import com.bluemoon.backend.domain.account.Account;
import com.bluemoon.backend.domain.insight.AiInsight;
import com.bluemoon.backend.domain.insight.DailyPick;
import com.bluemoon.backend.domain.stock.RankType;
import com.bluemoon.backend.domain.stock.Stock;
import com.bluemoon.backend.domain.stock.TopMover;
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
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Map;
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

    /**
     * 배포 직후에도(다음날 08:00 전까지) AI_INSIGHTS가 비어있지 않도록 앱 기동 시 한 번 실행한다 — MarketRankingBatchService와 동일 패턴.
     * "오늘의 추천 종목"(DAILY_PICKS)을 뽑을 때 top_movers(거래량 랭킹)를 참조하므로, MarketRankingBatchService의
     * onStartup()(@Order(1))이 먼저 끝난 뒤 실행되도록 @Order(2)로 명시. 순서가 안 맞으면 top_movers가 비어있어
     * DAILY_PICKS가 하나도 안 만들어지고(AI_INSIGHTS 자체는 생성됨에도) 대시보드에 아무것도 안 뜨는 문제가 있었음.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void onStartup() {
        generateDailyInsights();
    }

    /** 매일 08:00 KST에 실행. */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    @Transactional
    public void generateDailyInsights() {
        LocalDate today = LocalDate.now();

        // 기동 시 트리거(onStartup)가 배포할 때마다 반복 실행되므로, 오늘 이미 생성된 게 있으면 뉴스/LLM 호출은
        // 건너뛴다 — NewsData.io 무료 할당량(200 크레딧/일)이 재배포 몇 번 만에 소진되는 걸 막기 위함.
        // 단, "오늘의 추천 종목"(DAILY_PICKS) 배정은 생성을 건너뛴 경우에도 기존에 쌓인 인사이트로 계속 시도한다
        // — 여기서 같이 return 해버리면 이미 인사이트가 있는데도 대시보드에 추천이 하나도 안 뜨는 문제가 생김.
        if (aiInsightMapper.existsGeneratedAfter(today.atStartOfDay())) {
            log.info("오늘 이미 AI 인사이트가 생성되어 있어 생성 단계는 건너뜁니다.");
        } else {
            for (Stock stock : stockMapper.findAll()) {
                Optional<InsightGenerationService.Result> result = insightGenerationService.generate(stock.getCode(), stock.getName());
                if (result.isEmpty()) {
                    log.warn("AI 인사이트 생성 실패 — stockCode={}", stock.getCode());
                    continue;
                }
                aiInsightMapper.insert(new AiInsight(stock.getCode(), result.get().content(), result.get().sources()));
            }
        }

        assignDailyPicks(today);
    }

    /**
     * 계좌가 보유하지 않은, 인사이트가 있는 종목 중 거래량이 가장 많은 종목을 "새로 눈여겨볼 만한 종목"으로 추천한다.
     * 인사이트는 이번 실행에서 새로 생성했는지 여부와 무관하게 항상 "종목별 최신 인사이트"를 기준으로 삼는다.
     * 거래량은 top_movers(VOLUME 랭킹) 캐시에서만 알 수 있어, 랭킹에 없는 종목은 0으로 취급해 후순위로 민다
     * (거래량 랭킹에 없다고 후보에서 아예 제외하지는 않음 — 인사이트 존재 여부가 1차 조건).
     */
    private void assignDailyPicks(LocalDate today) {
        Map<String, AiInsight> latestByStock = latestInsightByStock();
        Map<String, Long> volumeByCode = volumeByStockCode();

        for (Account account : accountMapper.findAll()) {
            assignPickIfMissing(account.getId(), today, latestByStock, volumeByCode);
        }
    }

    /**
     * 방금 가입한 계좌에 오늘의 추천 종목을 바로 배정한다. 뉴스/LLM을 호출하는 게 아니라 이미 캐시된
     * AI_INSIGHTS/top_movers를 조회만 하므로 요청 경로(회원가입)에서 호출해도 CLAUDE.md 규칙에 어긋나지 않는다
     * — 그렇게 안 하면 다음 배치(재배포 또는 다음날 08:00)까지 새 계정은 대시보드에 아무 추천도 안 뜸.
     */
    public void assignPickForNewAccount(Long accountId) {
        assignPickIfMissing(accountId, LocalDate.now(), latestInsightByStock(), volumeByStockCode());
    }

    private void assignPickIfMissing(Long accountId, LocalDate today, Map<String, AiInsight> latestByStock, Map<String, Long> volumeByCode) {
        if (dailyPickMapper.findByAccountIdAndPickDate(accountId, today).isPresent()) {
            return;
        }
        Set<String> heldCodes = holdingMapper.findByAccountIdAndQuantityGreaterThan(accountId, 0L).stream()
                .map(h -> h.getStockCode())
                .collect(Collectors.toSet());

        latestByStock.values().stream()
                .filter(insight -> !heldCodes.contains(insight.getStockCode()))
                .max(Comparator.comparingLong(insight -> volumeByCode.getOrDefault(insight.getStockCode(), 0L)))
                .ifPresent(insight -> dailyPickMapper.insert(new DailyPick(accountId, insight.getId(), today)));
    }

    private Map<String, AiInsight> latestInsightByStock() {
        return aiInsightMapper.findLatestPerStock().stream()
                .collect(Collectors.toMap(AiInsight::getStockCode, insight -> insight));
    }

    private Map<String, Long> volumeByStockCode() {
        return topMoverMapper.findByRankTypeOrderByRank(RankType.VOLUME).stream()
                .collect(Collectors.toMap(TopMover::getStockCode, tm -> tm.getVolume() != null ? tm.getVolume() : 0L, (a, b) -> a));
    }
}
