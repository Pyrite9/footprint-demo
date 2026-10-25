package com.footprint.backend.dto;

import com.footprint.backend.entity.TeamMember;

// 과제 상세의 조 목록에 들어가는 조 하나의 모양입니다. 조 이름과 조장 이름을 펼쳐서 담습니다.
public record TeamSummaryResponse(Long id, String name, String leaderName) {

    // 조장의 조 참여 기록 하나를 조 요약으로 바꿉니다. 조 정보는 leader.getTeam()에서, 조장 이름은 leader.getMember()에서 꺼냅니다.
    public static TeamSummaryResponse from(TeamMember leader) {
        return new TeamSummaryResponse(leader.getTeam().getId(), leader.getTeam().getName(), leader.getMember().getName());
    }
}
