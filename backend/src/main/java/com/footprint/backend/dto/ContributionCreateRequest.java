package com.footprint.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 조장이 기여 항목을 등록할 때 보내는 요청 body의 모양입니다. teamMemberId는 담당 조원의 조 참여 번호입니다.
public record ContributionCreateRequest(
    @NotBlank @Size(max = 255) String title,
    @NotNull Long teamMemberId
) { }
