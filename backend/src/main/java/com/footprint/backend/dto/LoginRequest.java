package com.footprint.backend.dto;

import jakarta.validation.constraints.NotBlank;

// 로그인할 때 클라이언트가 보내는 요청 body의 모양입니다.
public record LoginRequest(
    @NotBlank String email,
    @NotBlank String password
) { }
