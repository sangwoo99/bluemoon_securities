package com.bluemoon.backend.service;

import com.bluemoon.backend.client.NewsDataClient;
import com.bluemoon.backend.client.OpenAiClient;
import com.bluemoon.backend.domain.insight.AiInsight;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * NewsData.io 뉴스 검색 + OpenAI 요약으로 AI 인사이트를 생성한다 (Python RAG 서비스 대체 — 오라클 클라우드 프리티어의
 * 메모리 제약으로 별도 벡터 검색 없이 최신 기사 상위 N건을 그대로 근거로 사용한다. 기존 RAG 파이프라인은
 * {@code archive/rag-service}에 백업, 컴퓨팅 자원 확보 시 재도입 가능).
 * 요청 경로에서는 절대 호출하지 않는다 — {@link InsightBatchService}(하루 1회 배치) 전용 (CLAUDE.md 절대 규칙).
 *
 * <p>뉴스 소스는 네이버 → 구글 뉴스 RSS(비공식, "개인적 용도"만 허용하는 저작권 문구 있음) → NewsData.io 순으로
 * 교체됐다. 네이버는 검색 API를 NCP로 이관하며 AI 입력 활용 자체를 약관으로 금지했고, 구글 뉴스 RSS는 정식
 * API가 아니라 상업적/자동화 용도가 불명확해 최종적으로 개인/상업적 이용을 이용약관에 명시적으로 허용하는
 * NewsData.io로 정착했다.</p>
 */
@Service
@RequiredArgsConstructor
public class InsightGenerationService {

    private static final String SYSTEM_PROMPT = """
            너는 증권 뉴스를 요약하는 리서치 보조원이다. 아래 뉴스 발췌를 근거로 종목에 대한 참고용 요약 정보를 작성하라.

            절대 규칙:
            - "매수하세요", "매도하세요", "지금이 매수 적기" 같은 단정적 투자 추천 문구를 쓰지 말 것.
            - 사실과 뉴스의 논조를 3~4문장으로 중립적으로 요약할 것 (근거 있는 설명 위주).
            - 제공된 뉴스에 없는 내용을 지어내지 말 것.
            - 한국어로 작성할 것.
            """;

    private static final int NEWS_SEARCH_SIZE = 10;
    private static final int TOP_K = 5;

    private final NewsDataClient newsDataClient;
    private final OpenAiClient openAiClient;

    /** 뉴스가 없거나 LLM 호출에 실패하면 빈 값을 반환한다 (배치가 나머지 종목을 계속 처리할 수 있도록). */
    public Optional<Result> generate(String stockCode, String stockName) {
        List<NewsDataClient.NewsArticle> articles = newsDataClient.searchNews(stockName, NEWS_SEARCH_SIZE);
        if (articles.isEmpty()) {
            return Optional.empty();
        }

        List<NewsDataClient.NewsArticle> topArticles = articles.stream().limit(TOP_K).toList();
        String context = topArticles.stream()
                .map(a -> "- [%s · %s] %s\n%s".formatted(a.source(), a.date(), a.title(), a.description()))
                .collect(Collectors.joining("\n\n"));

        String userPrompt = """
                종목: %s (%s)

                뉴스 발췌:
                %s

                위 뉴스를 바탕으로 참고용 요약 정보를 작성하라.
                """.formatted(stockName, stockCode, context);

        return openAiClient.chat(SYSTEM_PROMPT, userPrompt)
                .map(content -> new Result(content, toSources(topArticles)));
    }

    private List<AiInsight.InsightSource> toSources(List<NewsDataClient.NewsArticle> articles) {
        return articles.stream()
                .map(a -> new AiInsight.InsightSource(a.source(), a.date(), a.title(), a.url()))
                .distinct()
                .toList();
    }

    public record Result(String content, List<AiInsight.InsightSource> sources) {
    }
}
