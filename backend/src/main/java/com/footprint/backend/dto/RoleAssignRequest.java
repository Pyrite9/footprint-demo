package com.footprint.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 조장이 조원에게 역할을 지정할 때 보내는 요청 body의 모양입니다.
public record RoleAssignRequest(
    @NotBlank @Size(max = 255) String roleName
) { }
