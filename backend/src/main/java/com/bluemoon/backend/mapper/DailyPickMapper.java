package com.bluemoon.backend.mapper;

import com.bluemoon.backend.domain.insight.DailyPick;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DailyPickMapper {

    void insert(DailyPick dailyPick);

    List<DailyPick> findByAccountIdAndPickDate(@Param("accountId") Long accountId, @Param("pickDate") LocalDate pickDate);
}
