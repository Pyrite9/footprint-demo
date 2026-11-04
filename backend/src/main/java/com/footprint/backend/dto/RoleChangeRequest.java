package com.footprint.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 조장이 조원의 역할을 바꿀 때 보내는 요청 body의 모양입니다.
public record RoleChangeRequest(
    @NotBlank @Size(max = 255) String roleName
) { }
