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

    /** 해당 날짜에 이미 스냅샷이 있는 종목 코드 목록 — PriceHistoryBackfillService 등 다른 경로가 먼저 채워둔 것과 겹치지 않는지 확인용. */
    List<String> findStockCodesBySnapshotDate(@Param("snapshotDate") LocalDate snapshotDate);
}
