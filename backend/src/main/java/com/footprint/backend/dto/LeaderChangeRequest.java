package com.footprint.backend.dto;

import jakarta.validation.constraints.NotNull;

// 교수가 조장을 바꿀 때 보내는 요청 body의 모양입니다. teamMemberId는 새 조장의 조 참여 번호입니다.
public record LeaderChangeRequest(
    @NotNull Long teamMemberId
) { }
