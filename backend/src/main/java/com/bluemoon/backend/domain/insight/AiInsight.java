package com.bluemoon.backend.domain.insight;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 오직 배치(하루 1회)만 이 엔티티를 저장한다. 요청 경로(/api/insights/*)는 항상 조회만 한다 (CLAUDE.md 절대 규칙).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiInsight {

    private Long id;
    private String stockCode;
    private String content;
    private List<InsightSource> sources;
    private LocalDateTime generatedAt;

    public AiInsight(String stockCode, String content, List<InsightSource> sources) {
        this.stockCode = stockCode;
        this.content = content;
        this.sources = sources;
        this.generatedAt = LocalDateTime.now();
    }

    public record InsightSource(String name, String date, String title, String url) {
    }
}
