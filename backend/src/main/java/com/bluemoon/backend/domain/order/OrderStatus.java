package com.bluemoon.backend.domain.order;

public enum OrderStatus {
    /** 지정가 주문이 조건 충족 전 대기 중 (PENDING_ORDERS에만 존재, ORDERS에는 절대 나타나지 않음). */
    PENDING,
    FILLED,
    /** 체결 전 취소된 지정가 주문 (PENDING_ORDERS에만 존재). */
    CANCELLED
}
