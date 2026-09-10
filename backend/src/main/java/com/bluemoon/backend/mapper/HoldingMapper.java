package com.bluemoon.backend.mapper;

import com.bluemoon.backend.domain.holding.Holding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface HoldingMapper {

    List<Holding> findByAccountIdAndQuantityGreaterThan(@Param("accountId") Long accountId, @Param("quantity") Long quantity);

    /** HOLDINGS 행을 SELECT ... FOR UPDATE로 잠근다. ACCOUNTS 락을 먼저 획득한 뒤에만 호출할 것 (데드락 방지). */
    Optional<Holding> findByAccountIdAndStockCodeForUpdate(@Param("accountId") Long accountId, @Param("stockCode") String stockCode);

    void insert(Holding holding);

    void update(Holding holding);
}
