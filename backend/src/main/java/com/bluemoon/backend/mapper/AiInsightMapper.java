package com.bluemoon.backend.mapper;

import com.bluemoon.backend.domain.insight.AiInsight;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface AiInsightMapper {

    void insert(AiInsight aiInsight);

    Optional<AiInsight> findById(@Param("id") Long id);

    Optional<AiInsight> findFirstByStockCodeOrderByGeneratedAtDesc(@Param("stockCode") String stockCode);

    boolean existsGeneratedAfter(@Param("threshold") LocalDateTime threshold);
}
