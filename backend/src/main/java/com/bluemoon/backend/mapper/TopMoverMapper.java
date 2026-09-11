package com.bluemoon.backend.mapper;

import com.bluemoon.backend.domain.stock.RankType;
import com.bluemoon.backend.domain.stock.TopMover;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TopMoverMapper {

    void insert(TopMover topMover);

    void deleteByRankType(@Param("rankType") RankType rankType);

    List<String> findStockCodesByRankTypeOrderByRank(@Param("rankType") RankType rankType);
}
