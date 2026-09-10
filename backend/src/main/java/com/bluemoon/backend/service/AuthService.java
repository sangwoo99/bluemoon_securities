package com.bluemoon.backend.service;

import com.bluemoon.backend.common.exception.ApiException;
import com.bluemoon.backend.common.exception.ErrorCode;
import com.bluemoon.backend.domain.account.Account;
import com.bluemoon.backend.domain.user.User;
import com.bluemoon.backend.dto.request.LoginRequest;
import com.bluemoon.backend.dto.request.SignupRequest;
import com.bluemoon.backend.dto.response.LoginResponse;
import com.bluemoon.backend.dto.response.SignupResponse;
import com.bluemoon.backend.mapper.AccountMapper;
import com.bluemoon.backend.mapper.UserMapper;
import com.bluemoon.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.seed-cash-balance}")
    private BigDecimal seedCashBalance;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userMapper.existsByEmail(request.email())) {
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = new User(request.email(), passwordEncoder.encode(request.password()), request.name());
        userMapper.insert(user);

        Account account = new Account(user.getId(), seedCashBalance);
        accountMapper.insert(account);

        return new SignupResponse(user.getId(), account.getId());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.findByEmail(request.email())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        return new LoginResponse(
                jwtTokenProvider.createAccessToken(user.getId()),
                jwtTokenProvider.createRefreshToken(user.getId())
        );
    }
}
