package com.bluemoon.backend.mapper;

import com.bluemoon.backend.domain.user.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserMapper {

    void insert(User user);

    Optional<User> findByEmail(@Param("email") String email);

    Optional<User> findById(@Param("id") Long id);

    boolean existsByEmail(@Param("email") String email);
}
