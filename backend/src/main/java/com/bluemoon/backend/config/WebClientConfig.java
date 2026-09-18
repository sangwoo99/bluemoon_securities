package com.bluemoon.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
public class WebClientConfig {

    /**
     * KIS는 기본(풀링) 커넥션을 재사용하면 간헐적으로 응답 없이 10초 타임아웃이 나는 문제가 있었다
     * (재사용해둔 커넥션이 KIS 쪽에서 조용히 끊긴 뒤 그걸 모르고 재사용하다 멈추는 것으로 추정).
     * 호출 빈도 자체가 낮아(배치가 종목당 1.1초 간격으로 순차 호출) 매번 새 커넥션을 맺는 비용이
     * 무시할 만한 수준이라, 풀링 없이 요청마다 새 커넥션을 맺도록 한다.
     */
    @Bean
    public WebClient kisWebClient(@Value("${app.kis.base-url}") String baseUrl) {
        HttpClient httpClient = HttpClient.create(ConnectionProvider.newConnection());
        return WebClient.builder().baseUrl(baseUrl).clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }

    @Bean
    public WebClient openAiWebClient(@Value("${app.openai.base-url}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }

    @Bean
    public WebClient newsDataWebClient(@Value("${app.newsdata.base-url}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}
