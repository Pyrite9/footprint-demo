package com.footprint.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 조장이 보고서를 만들 때 보내는 요청 body의 모양입니다.
public record ReportCreateRequest(
    @NotBlank @Size(max = 255) String title
) { }
