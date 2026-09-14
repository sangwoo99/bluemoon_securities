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
    private final InsightBatchService insightBatchService;

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

        // 다음 배치(재배포 또는 다음날 08:00)까지 기다리지 않고 가입 직후 바로 "오늘의 추천 종목"을 배정한다.
        // LLM/뉴스 호출 없이 기존 캐시(AI_INSIGHTS, top_movers)만 조회하므로 요청 경로 금지 규칙과 무관하다.
        insightBatchService.assignPickForNewAccount(account.getId());

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
