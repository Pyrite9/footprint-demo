package com.footprint.backend.controller;

import com.footprint.backend.dto.LoginRequest;
import com.footprint.backend.dto.LoginResponse;
import com.footprint.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 로그인 요청을 받아 token을 돌려줍니다. SecurityConfig에서 /auth/**는 token 없이 열어 두었습니다.
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // 이메일과 비밀번호를 받아 token을 돌려줍니다.
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return new LoginResponse(authService.login(request.email(), request.password()));
    }
}
