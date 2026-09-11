package com.bluemoon.backend.mapper;

import com.bluemoon.backend.domain.order.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface OrderMapper {

    void insert(Order order);

    List<Order> findByAccountId(@Param("accountId") Long accountId, @Param("offset") int offset, @Param("limit") int limit);

    long countByAccountId(@Param("accountId") Long accountId);

    List<Order> findByAccountIdAndStockCode(@Param("accountId") Long accountId, @Param("stockCode") String stockCode,
                                             @Param("offset") int offset, @Param("limit") int limit);

    long countByAccountIdAndStockCode(@Param("accountId") Long accountId, @Param("stockCode") String stockCode);

    List<Order> findAllByAccountIdAndStockCode(@Param("accountId") Long accountId, @Param("stockCode") String stockCode);

    Optional<Order> findByIdAndAccountId(@Param("id") Long id, @Param("accountId") Long accountId);

    /** 주어진 주문 id들 중 이미 취소 레코드가 존재하는(=cancel_of_order_id로 참조된) 원주문 id 집합. */
    List<Long> findCancelOfOrderIds(@Param("accountId") Long accountId, @Param("orderIds") List<Long> orderIds);
}
