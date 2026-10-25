package com.footprint.backend.dto;

import jakarta.validation.constraints.NotBlank;

// 초대 코드로 조에 합류할 때 보내는 요청 body의 모양입니다.
public record TeamJoinRequest(
    @NotBlank String inviteCode
) { }
