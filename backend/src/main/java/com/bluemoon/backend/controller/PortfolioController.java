package com.bluemoon.backend.controller;

import com.bluemoon.backend.common.ApiResponse;
import com.bluemoon.backend.dto.response.PortfolioSummaryResponse;
import com.bluemoon.backend.dto.response.TrendPointResponse;
import com.bluemoon.backend.security.CurrentUserProvider;
import com.bluemoon.backend.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/summary")
    public ApiResponse<PortfolioSummaryResponse> getSummary() {
        return ApiResponse.ok(portfolioService.getSummary(currentUserProvider.getUserId()));
    }

    @GetMapping("/trend")
    public ApiResponse<List<TrendPointResponse>> getTrend(@RequestParam(defaultValue = "1M") String period) {
        return ApiResponse.ok(portfolioService.getTrend(currentUserProvider.getUserId(), period));
    }
}
