package com.bluemoon.backend.mapper;

import com.bluemoon.backend.domain.order.PendingOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Mapper
public interface PendingOrderMapper {

    void insert(PendingOrder order);

    /** 상태 전이(PENDING -> FILLED/CANCELLED) 반영. ORDERS와 달리 append-only가 아니므로 UPDATE 허용. */
    void update(PendingOrder order);

    /** PENDING_ORDERS 행을 SELECT ... FOR UPDATE로 잠근다. 취소·체결 처리 중 경합을 막는다. */
    Optional<PendingOrder> findByIdAndAccountIdForUpdate(@Param("id") Long id, @Param("accountId") Long accountId);

    Optional<PendingOrder> findByIdForUpdate(@Param("id") Long id);

    /** 매칭 배치가 순회할 전체 계좌의 미체결 지정가 주문. */
    List<PendingOrder> findAllPending();

    /** 거래내역에 노출할 행 — 이미 체결되어 ORDERS에 별도로 기록된 행은 제외(중복 표시 방지). */
    List<PendingOrder> findVisibleByAccountId(@Param("accountId") Long accountId);

    List<PendingOrder> findVisibleByAccountIdAndStockCode(@Param("accountId") Long accountId, @Param("stockCode") String stockCode);

    /** 미체결 매수 주문이 예약해둔 현금 총액(가용 잔고 계산용). */
    BigDecimal sumReservedCash(@Param("accountId") Long accountId);

    /** 미체결 매도 주문이 예약해둔 수량(가용 보유수량 계산용). */
    long sumReservedQuantity(@Param("accountId") Long accountId, @Param("stockCode") String stockCode);
}
