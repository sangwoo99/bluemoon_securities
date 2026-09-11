package com.bluemoon.backend.mapper;

import com.bluemoon.backend.domain.watchlist.Watchlist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WatchlistMapper {

    void insert(Watchlist watchlist);

    void deleteByAccountIdAndStockCode(@Param("accountId") Long accountId, @Param("stockCode") String stockCode);

    boolean existsByAccountIdAndStockCode(@Param("accountId") Long accountId, @Param("stockCode") String stockCode);

    List<String> findStockCodesByAccountId(@Param("accountId") Long accountId);
}
