package com.bluemoon.backend.controller;

import com.bluemoon.backend.common.ApiResponse;
import com.bluemoon.backend.dto.response.InsightResponse;
import com.bluemoon.backend.security.CurrentUserProvider;
import com.bluemoon.backend.service.InsightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 서비스 장애가 대시보드 전체를 막지 않도록, 조회 실패 시에도 success:true, data:null로 응답한다
 * (docs/api-spec.md 5장, 스토리보드 2.1 에러 처리).
 */
@Slf4j
@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/today")
    public ApiResponse<InsightResponse> getToday() {
        try {
            return ApiResponse.ok(insightService.getToday(currentUserProvider.getUserId()));
        } catch (Exception e) {
            log.warn("오늘의 AI 인사이트 조회 실패", e);
            return ApiResponse.ok(null);
        }
    }

    @GetMapping("/{code}")
    public ApiResponse<InsightResponse> getForStock(@PathVariable String code) {
        try {
            return ApiResponse.ok(insightService.getForStock(code));
        } catch (Exception e) {
            log.warn("종목 AI 인사이트 조회 실패 — code={}", code, e);
            return ApiResponse.ok(null);
        }
    }
}
