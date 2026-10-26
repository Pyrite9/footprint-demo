package com.footprint.backend.dto;

import jakarta.validation.constraints.NotBlank;

// 구글 로그인할 때 클라이언트가 보내는 요청 body의 모양입니다. credential은 화면의 구글 버튼이 받은 ID token입니다.
public record GoogleLoginRequest(
    @NotBlank String credential
) { }
