package com.footprint.backend.service;

import java.time.LocalDateTime;

// 로그인과 재발급이 만든 token 두 개입니다. accessToken은 응답 body로, refreshToken은 cookie로 나갑니다(AuthController).
// refreshTokenExpiresAt은 cookie의 Max-Age를 계산하는 데 씁니다.
public record LoginTokens(String accessToken, String refreshToken, LocalDateTime refreshTokenExpiresAt) { }
