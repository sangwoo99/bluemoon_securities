package com.bluemoon.backend.mapper;

import com.bluemoon.backend.domain.insight.AiInsight;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface AiInsightMapper {

    void insert(AiInsight aiInsight);

    Optional<AiInsight> findById(@Param("id") Long id);

    Optional<AiInsight> findFirstByStockCodeOrderByGeneratedAtDesc(@Param("stockCode") String stockCode);

    boolean existsGeneratedAfter(@Param("threshold") LocalDateTime threshold);

    /** 종목별로 가장 최근에 생성된 인사이트 1건씩만 반환한다 (오늘 새로 생성했든 예전에 생성했든 상관없이 "지금 쓸 수 있는 최신 것"). */
    List<AiInsight> findLatestPerStock();
}
