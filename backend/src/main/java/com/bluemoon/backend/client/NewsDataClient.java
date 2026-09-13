package com.bluemoon.backend.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
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

    private final WebClient webClient;
    private final String apiKey;

    public NewsDataClient(WebClient newsDataWebClient, @Value("${app.newsdata.api-key}") String apiKey) {
        this.webClient = newsDataWebClient;
        this.apiKey = apiKey;
    }

    /** 종목명 관련 최신 한국어 뉴스를 검색한다. 키 미설정/장애 시 빈 리스트를 반환한다. */
    public List<NewsArticle> searchNews(String stockName, int size) {
        if (apiKey.isBlank()) {
            return List.of();
        }

        try {
            LatestNewsResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/1/latest")
                            .queryParam("apikey", apiKey)
                            .queryParam("q", stockName)
                            .queryParam("language", "ko")
                            .queryParam("size", size)
                            .build())
                    .retrieve()
                    .bodyToMono(LatestNewsResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response == null || !"success".equals(response.status()) || response.results() == null) {
                log.warn("NewsData.io 검색 실패 — stockName={}, status={}", stockName, response != null ? response.status() : "응답 없음");
                return List.of();
            }
            return response.results().stream().map(this::toArticle).toList();
        } catch (WebClientResponseException e) {
            log.warn("NewsData.io 검색 실패 — stockName={}, status={}, body={}", stockName, e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.warn("NewsData.io 검색 중 오류 — stockName={}, error={}", stockName, e.getMessage());
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
