package com.bluemoon.backend.mapper;

import com.bluemoon.backend.domain.snapshot.AccountSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AccountSnapshotMapper {

    void insert(AccountSnapshot accountSnapshot);

    List<AccountSnapshot> findByAccountIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
            @Param("accountId") Long accountId, @Param("from") LocalDate from);

    boolean existsBySnapshotDate(@Param("snapshotDate") LocalDate snapshotDate);
}
