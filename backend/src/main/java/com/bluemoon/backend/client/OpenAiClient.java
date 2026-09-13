package com.bluemoon.backend.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * OpenAI Chat Completions API 클라이언트. AI 인사이트 요약 생성 전용 — 요청 경로에서는 절대 호출하지 않고,
 * {@link com.bluemoon.backend.service.InsightGenerationService}를 거쳐 배치에서만 사용한다.
 */
@Slf4j
@Component
public class OpenAiClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    public OpenAiClient(
            WebClient openAiWebClient,
            @Value("${app.openai.api-key}") String apiKey,
            @Value("${app.openai.model}") String model
    ) {
        this.webClient = openAiWebClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    /** 주어진 프롬프트로 요약을 생성한다. 키 미설정/장애 시 빈 값을 반환한다. */
    public Optional<String> chat(String systemPrompt, String userPrompt) {
        if (apiKey.isBlank()) {
            return Optional.empty();
        }

        try {
            ChatResponse response = webClient.post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(new ChatRequest(model, List.of(
                            new ChatMessage("system", systemPrompt),
                            new ChatMessage("user", userPrompt)
                    ), 0.3))
                    .retrieve()
                    .bodyToMono(ChatResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                log.warn("OpenAI 응답 없음");
                return Optional.empty();
            }
            return Optional.ofNullable(response.choices().get(0).message().content());
        } catch (WebClientResponseException e) {
            log.warn("OpenAI 호출 실패 — status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("OpenAI 호출 중 오류 — error={}", e.getMessage());
            return Optional.empty();
        }
    }

    private record ChatRequest(String model, List<ChatMessage> messages, double temperature) {
    }

    private record ChatMessage(String role, String content) {
    }

    private record ChatResponse(List<Choice> choices) {
    }

    private record Choice(ChatMessage message) {
    }
}
