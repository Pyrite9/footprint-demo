package com.footprint.backend.dto;

import com.footprint.backend.entity.RoleHistory;

import java.time.LocalDateTime;

// 역할 이력 한 줄의 모양입니다. previousRoleName은 바뀌기 전의 역할명, changedAt은 바뀐 시각입니다.
public record RoleHistoryResponse(Long id, String previousRoleName, LocalDateTime changedAt) {

    // 역할 이력 Entity를 응답 DTO로 바꿉니다.
    public static RoleHistoryResponse from(RoleHistory roleHistory) {
        return new RoleHistoryResponse(roleHistory.getId(), roleHistory.getPreviousRoleName(), roleHistory.getChangedAt());
    }
}
