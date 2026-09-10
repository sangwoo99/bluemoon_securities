package com.bluemoon.backend.mapper;

import com.bluemoon.backend.domain.order.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper {

    void insert(Order order);

    List<Order> findByAccountId(@Param("accountId") Long accountId, @Param("offset") int offset, @Param("limit") int limit);

    long countByAccountId(@Param("accountId") Long accountId);

    List<Order> findByAccountIdAndStockCode(@Param("accountId") Long accountId, @Param("stockCode") String stockCode,
                                             @Param("offset") int offset, @Param("limit") int limit);

    long countByAccountIdAndStockCode(@Param("accountId") Long accountId, @Param("stockCode") String stockCode);

    List<Order> findAllByAccountIdAndStockCode(@Param("accountId") Long accountId, @Param("stockCode") String stockCode);
}
