package com.bluemoon.backend.mapper;

import com.bluemoon.backend.domain.stock.Stock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface StockMapper {

    void insert(Stock stock);

    Optional<Stock> findByCode(@Param("code") String code);

    List<Stock> findAll();

    List<Stock> findAllByCodes(@Param("codes") List<String> codes);

    void update(Stock stock);
}
