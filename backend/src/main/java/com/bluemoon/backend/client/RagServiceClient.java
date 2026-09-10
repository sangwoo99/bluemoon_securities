package com.bluemoon.backend.client;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Python RAG 서비스 내부 호출 전용 클라이언트. 외부(프론트엔드)에는 노출되지 않는다 (docs/api-spec.md 6장).
 * 요청 경로에서는 절대 호출하지 않고, 배치({@link com.bluemoon.backend.service.InsightBatchService})에서만 사용한다.
 */
@Component
public class RagServiceClient {

    private final WebClient webClient;

    public RagServiceClient(WebClient ragServiceWebClient) {
        this.webClient = ragServiceWebClient;
    }

    public GenerateInsightResponse generateInsight(String stockCode, String stockName) {
        return webClient.post()
                .uri("/generate-insight")
                .bodyValue(new GenerateInsightRequest(stockCode, stockName))
                .retrieve()
                .bodyToMono(GenerateInsightResponse.class)
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> Mono.empty())
                .block();
    }

    public record GenerateInsightRequest(String stockCode, String stockName) {
    }

    public record GenerateInsightResponse(String content, List<Source> sources) {
        public record Source(String name, String date) {
        }
    }
}
