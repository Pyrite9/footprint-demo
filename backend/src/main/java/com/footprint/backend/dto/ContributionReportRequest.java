package com.footprint.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 담당 조원이 완료 보고할 때 보내는 요청 body의 모양입니다.
public record ContributionReportRequest(
    @NotBlank @Size(max = 2000) String memo
) { }
