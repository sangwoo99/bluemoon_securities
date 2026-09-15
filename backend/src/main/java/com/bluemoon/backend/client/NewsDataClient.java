package com.bluemoon.backend.client;

import com.bluemoon.backend.common.KstClock;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * NewsData.io 뉴스 검색 API 클라이언트. AI 인사이트({@link com.bluemoon.backend.service.InsightGenerationService})의
 * 근거 기사 수집 전용 — 요청 경로에서는 절대 호출하지 않고, 배치에서만 사용한다.
 *
 * <p>원래는 네이버 뉴스 검색 API를 썼으나, 네이버가 2026-07-31부로 개발자센터에서 검색 API 신규 발급을 중단하고
 * NCP "NAVER API HUB"로 이관했고, 2026-09-07 개정 약관에서 검색 결과를 AI 입력/요약에 활용하는 것 자체를
 * 금지해 지금 하는 일(뉴스 검색 → LLM 요약)과 정면으로 충돌한다. NewsData.io는 이용약관에 개인/상업적 목적
 * 데이터 활용을 명시적으로 허용하고, 무료 플랜(200 크레딧/일)으로도 기사 스니펫을 포함한 응답을 준다.</p>
 */
@Slf4j
@Component
public class NewsDataClient {

    private static final DateTimeFormatter PUB_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** /latest에 최근 기사가 하나도 없을 때만 뒤지는 과거 범위 — "최신 뉴스가 없어도 가장 과거의 것이라도" 보여주기 위함. */
    private static final int ARCHIVE_LOOKBACK_YEARS = 2;

    private final WebClient webClient;
    private final String apiKey;

    public NewsDataClient(WebClient newsDataWebClient, @Value("${app.newsdata.api-key}") String apiKey) {
        this.webClient = newsDataWebClient;
        this.apiKey = apiKey;
    }

    /**
     * 종목명 관련 최신 한국어 뉴스를 검색한다. /latest(최근 48시간)에 결과가 없으면 /archive로 과거
     * {@value #ARCHIVE_LOOKBACK_YEARS}년치를 넓게 훑어 가장 오래된 기사라도 근거로 쓴다 — 거래량이 적어
     * 최근 보도가 없는 종목도 "최근 뉴스가 없어 인사이트를 불러올 수 없습니다"로 비워두지 않기 위함.
     * archive는 NewsData.io 유료 플랜부터 제공되므로, 무료 플랜에서는 이 폴백이 조용히 빈 리스트로 끝난다
     * (아래 개별 메서드의 예외 처리와 동일하게 배치 흐름을 막지 않음).
     * 키 미설정/장애 시에도 빈 리스트를 반환한다.
     */
    public List<NewsArticle> searchNews(String stockName, int size) {
        if (apiKey.isBlank()) {
            return List.of();
        }

        List<NewsArticle> latest = search("/api/1/latest", stockName, size, null, null);
        if (!latest.isEmpty()) {
            return latest;
        }

        LocalDate to = KstClock.today();
        LocalDate from = to.minusYears(ARCHIVE_LOOKBACK_YEARS);
        return search("/api/1/archive", stockName, size, from, to);
    }

    private List<NewsArticle> search(String path, String stockName, int size, LocalDate fromDate, LocalDate toDate) {
        try {
            LatestNewsResponse response = webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path)
                                .queryParam("apikey", apiKey)
                                .queryParam("q", stockName)
                                .queryParam("language", "ko")
                                .queryParam("size", size);
                        if (fromDate != null) {
                            uriBuilder.queryParam("from_date", fromDate);
                        }
                        if (toDate != null) {
                            uriBuilder.queryParam("to_date", toDate);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .bodyToMono(LatestNewsResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response == null || !"success".equals(response.status()) || response.results() == null) {
                log.warn("NewsData.io 검색 실패 — path={}, stockName={}, status={}", path, stockName, response != null ? response.status() : "응답 없음");
                return List.of();
            }
            return response.results().stream().map(this::toArticle).toList();
        } catch (WebClientResponseException e) {
            log.warn("NewsData.io 검색 실패 — path={}, stockName={}, status={}, body={}", path, stockName, e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.warn("NewsData.io 검색 중 오류 — path={}, stockName={}, error={}", path, stockName, e.getMessage());
            return List.of();
        }
    }

    private NewsArticle toArticle(NewsDataArticle a) {
        String title = a.title() != null ? a.title() : "";
        String description = a.description() != null ? a.description() : "";
        String source = a.sourceName() != null && !a.sourceName().isBlank() ? a.sourceName() : "NewsData.io";
        return new NewsArticle(title, description, source, parseDate(a.pubDate()), a.link());
    }

    private String parseDate(String pubDate) {
        try {
            return LocalDateTime.parse(pubDate, PUB_DATE_FORMAT).toLocalDate().toString();
        } catch (DateTimeParseException | NullPointerException e) {
            return pubDate;
        }
    }

    public record NewsArticle(String title, String description, String source, String date, String url) {
    }

    private record LatestNewsResponse(String status, List<NewsDataArticle> results) {
    }

    private record NewsDataArticle(
            String title,
            String description,
            String link,
            @JsonProperty("source_name") String sourceName,
            String pubDate
    ) {
    }
}
