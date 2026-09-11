package com.bluemoon.backend.mapper;

import com.bluemoon.backend.domain.stock.TopMover;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TopMoverMapper {

    void insert(TopMover topMover);

    void deleteAll();

    List<String> findStockCodesOrderByRank();
}
