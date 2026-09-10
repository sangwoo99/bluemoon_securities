package com.bluemoon.backend.mapper;

import com.bluemoon.backend.domain.account.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface AccountMapper {

    void insert(Account account);

    Optional<Account> findByUserId(@Param("userId") Long userId);

    /** ACCOUNTS 행을 SELECT ... FOR UPDATE로 잠근다. 락 획득 순서는 항상 ACCOUNTS -> HOLDINGS (CLAUDE.md 절대 규칙). */
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    List<Account> findAll();

    void update(Account account);
}
