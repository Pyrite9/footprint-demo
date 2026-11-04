package com.footprint.backend.dto;

import com.footprint.backend.entity.LeaderHistory;

import java.time.LocalDateTime;

// 조장 교체 이력 한 줄의 모양입니다. 이전 조장과 새 조장은 화면에 바로 쓰도록 이름으로 펼칩니다.
public record LeaderHistoryResponse(Long id, String previousLeaderName, String newLeaderName, LocalDateTime changedAt) {

    // 조장 교체 이력 Entity를 응답 DTO로 바꿉니다.
    public static LeaderHistoryResponse from(LeaderHistory leaderHistory) {
        return new LeaderHistoryResponse(leaderHistory.getId(), leaderHistory.getPreviousLeader().getMember().getName(),
                leaderHistory.getNewLeader().getMember().getName(), leaderHistory.getChangedAt());
    }
}
