package com.bluemoon.backend.controller;

import com.bluemoon.backend.common.ApiResponse;
import com.bluemoon.backend.dto.response.HoldingResponse;
import com.bluemoon.backend.security.CurrentUserProvider;
import com.bluemoon.backend.service.HoldingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/holdings")
@RequiredArgsConstructor
public class HoldingController {

    private final HoldingService holdingService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ApiResponse<List<HoldingResponse>> getHoldings() {
        return ApiResponse.ok(holdingService.getHoldings(currentUserProvider.getUserId()));
    }
}
