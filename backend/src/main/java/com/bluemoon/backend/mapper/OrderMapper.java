package com.bluemoon.backend.mapper;

import com.bluemoon.backend.domain.order.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper {

    void insert(Order order);

    List<Order> findAllByAccountIdAndStockCode(@Param("accountId") Long accountId, @Param("stockCode") String stockCode);

    /** 거래내역 조회용 — PENDING_ORDERS의 미체결/취소 건과 Java에서 합쳐 페이지네이션하므로 이 계좌의 체결 건 전체를 가져온다. */
    List<Order> findAllByAccountId(@Param("accountId") Long accountId);
}
