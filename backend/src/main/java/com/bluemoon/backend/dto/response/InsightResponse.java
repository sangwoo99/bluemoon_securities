package com.bluemoon.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record InsightResponse(
        String stockCode,
        String stockName,
        String content,
        List<InsightSourceResponse> sources,
        LocalDateTime generatedAt
) {
    public record InsightSourceResponse(String name, String date, String title, String url) {
    }
}
