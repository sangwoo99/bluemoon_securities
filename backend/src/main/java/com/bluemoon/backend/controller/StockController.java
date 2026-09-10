package com.bluemoon.backend.controller;

import com.bluemoon.backend.common.ApiResponse;
import com.bluemoon.backend.dto.response.PricePointResponse;
import com.bluemoon.backend.dto.response.StockResponse;
import com.bluemoon.backend.dto.response.TradeResponse;
import com.bluemoon.backend.security.CurrentUserProvider;
import com.bluemoon.backend.service.OrderService;
import com.bluemoon.backend.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final OrderService orderService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/api/stocks/{code}")
    public ApiResponse<StockResponse> getStock(@PathVariable String code) {
        return ApiResponse.ok(stockService.getStock(code));
    }

    @GetMapping("/api/stocks/{code}/price-history")
    public ApiResponse<List<PricePointResponse>> getPriceHistory(@PathVariable String code, @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(stockService.getPriceHistory(code, days));
    }

    @GetMapping("/api/trades/{code}")
    public ApiResponse<List<TradeResponse>> getTrades(@PathVariable String code) {
        return ApiResponse.ok(orderService.getTradesForStock(currentUserProvider.getUserId(), code));
    }
}
