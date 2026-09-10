package com.bluemoon.backend.mapper;

import com.bluemoon.backend.domain.snapshot.PriceSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PriceSnapshotMapper {

    void insert(PriceSnapshot priceSnapshot);

    List<PriceSnapshot> findByStockCodeAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
            @Param("stockCode") String stockCode, @Param("from") LocalDate from);
}
