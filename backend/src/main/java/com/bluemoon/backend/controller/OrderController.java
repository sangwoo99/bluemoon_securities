package com.bluemoon.backend.controller;

import com.bluemoon.backend.common.ApiResponse;
import com.bluemoon.backend.dto.request.CreateOrderRequest;
import com.bluemoon.backend.dto.response.CancelOrderResponse;
import com.bluemoon.backend.dto.response.CreateOrderResponse;
import com.bluemoon.backend.dto.response.OrderHistoryResponse;
import com.bluemoon.backend.dto.response.PageResponse;
import com.bluemoon.backend.security.CurrentUserProvider;
import com.bluemoon.backend.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateOrderResponse> placeOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok(orderService.placeOrder(currentUserProvider.getUserId(), request));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderHistoryResponse>> getOrders(
            @RequestParam(required = false) String code,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(orderService.getOrderHistory(currentUserProvider.getUserId(), code, page, size));
    }

    @DeleteMapping("/{orderId}")
    public ApiResponse<CancelOrderResponse> cancelOrder(@PathVariable Long orderId) {
        return ApiResponse.ok(orderService.cancelOrder(currentUserProvider.getUserId(), orderId));
    }
}
