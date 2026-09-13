package com.bluemoon.backend.domain.order;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 지정가 주문의 미체결 대기 티켓. ORDERS(체결 완료 거래 원장)와 달리 append-only가 아니다 —
 * PENDING -> FILLED/CANCELLED 상태 전이를 이 행 자체에 UPDATE로 반영한다.
 * append-only 제약은 체결이 실제로 일어난 거래만 기록하는 ORDERS 테이블에만 적용된다 (CLAUDE.md 절대 규칙).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingOrder {

    private Long id;
    private Long accountId;
    private String stockCode;
    private OrderSide side;
    private Long quantity;
    private BigDecimal limitPrice;
    private OrderStatus status;
    private Long filledOrderId;
    private LocalDateTime orderedAt;
    private LocalDateTime resolvedAt;

    public static PendingOrder create(Long accountId, String stockCode, OrderSide side, Long quantity, BigDecimal limitPrice) {
        PendingOrder order = new PendingOrder();
        order.accountId = accountId;
        order.stockCode = stockCode;
        order.side = side;
        order.quantity = quantity;
        order.limitPrice = limitPrice;
        order.status = OrderStatus.PENDING;
        order.orderedAt = LocalDateTime.now();
        return order;
    }

    /** 매수는 현재가가 지정가 이하로 내려왔을 때, 매도는 지정가 이상으로 올랐을 때 체결 조건을 만족한다. */
    public boolean matches(BigDecimal currentPrice) {
        return side == OrderSide.BUY
                ? currentPrice.compareTo(limitPrice) <= 0
                : currentPrice.compareTo(limitPrice) >= 0;
    }

    public void markFilled(Long filledOrderId) {
        this.status = OrderStatus.FILLED;
        this.filledOrderId = filledOrderId;
        this.resolvedAt = LocalDateTime.now();
    }

    public void markCancelled() {
        this.status = OrderStatus.CANCELLED;
        this.resolvedAt = LocalDateTime.now();
    }
}
