package com.bluemoon.backend.controller;

import com.bluemoon.backend.common.ApiResponse;
import com.bluemoon.backend.dto.request.LoginRequest;
import com.bluemoon.backend.dto.request.SignupRequest;
import com.bluemoon.backend.dto.response.LoginResponse;
import com.bluemoon.backend.dto.response.SignupResponse;
import com.bluemoon.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }
}
