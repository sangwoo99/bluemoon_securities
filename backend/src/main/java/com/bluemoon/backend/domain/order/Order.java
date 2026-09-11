package com.bluemoon.backend.domain.order;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** append-only. UPDATE/DELETE 금지 — 취소는 별도 레코드로 추가한다 (CLAUDE.md 절대 규칙). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    private Long id;
    private Long accountId;
    private String stockCode;
    private OrderSide side;
    private OrderType orderType;
    private Long quantity;
    private BigDecimal limitPrice;
    private BigDecimal filledPrice;
    private Long filledQuantity;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private Long cancelOfOrderId;
    private LocalDateTime orderedAt;

    public static Order filled(Long accountId, String stockCode, OrderSide side, OrderType orderType,
                                Long quantity, BigDecimal limitPrice, BigDecimal filledPrice) {
        Order order = new Order();
        order.accountId = accountId;
        order.stockCode = stockCode;
        order.side = side;
        order.orderType = orderType;
        order.quantity = quantity;
        order.limitPrice = limitPrice;
        order.filledPrice = filledPrice;
        order.filledQuantity = quantity;
        order.totalAmount = filledPrice.multiply(BigDecimal.valueOf(quantity));
        order.status = OrderStatus.FILLED;
        order.orderedAt = LocalDateTime.now();
        return order;
    }

    /**
     * 주문 취소 = 원주문과 반대 방향의 거래를 같은 체결가로 넣어 현금/보유수량을 원상복구하는 새 레코드.
     * 원주문 행은 건드리지 않는다(append-only) — 이 레코드가 cancelOfOrderId로 원주문을 참조한다.
     */
    public static Order cancellationOf(Order original) {
        Order order = new Order();
        order.accountId = original.accountId;
        order.stockCode = original.stockCode;
        order.side = original.side == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
        order.orderType = original.orderType;
        order.quantity = original.filledQuantity;
        order.limitPrice = null;
        order.filledPrice = original.filledPrice;
        order.filledQuantity = original.filledQuantity;
        order.totalAmount = original.totalAmount;
        order.status = OrderStatus.CANCELLED;
        order.cancelOfOrderId = original.id;
        order.orderedAt = LocalDateTime.now();
        return order;
    }
}
