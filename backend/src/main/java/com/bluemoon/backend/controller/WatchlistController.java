package com.bluemoon.backend.controller;

import com.bluemoon.backend.common.ApiResponse;
import com.bluemoon.backend.dto.response.StockResponse;
import com.bluemoon.backend.security.CurrentUserProvider;
import com.bluemoon.backend.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ApiResponse<List<StockResponse>> getMyWatchlist() {
        return ApiResponse.ok(watchlistService.getMyWatchlist(currentUserProvider.getUserId()));
    }

    @PostMapping("/{code}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> add(@PathVariable String code) {
        watchlistService.add(currentUserProvider.getUserId(), code);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{code}")
    public ApiResponse<Void> remove(@PathVariable String code) {
        watchlistService.remove(currentUserProvider.getUserId(), code);
        return ApiResponse.ok(null);
    }
}
