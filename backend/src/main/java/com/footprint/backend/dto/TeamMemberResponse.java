package com.footprint.backend.dto;

import com.footprint.backend.entity.TeamMember;

// 조원 목록의 조원 한 명 모양입니다. id는 조 참여 기록 번호, memberId는 회원 번호입니다.
public record TeamMemberResponse(Long id, Long memberId, String name, String roleName, boolean leader) {

    // 조 참여 기록 Entity를 응답 DTO로 바꿉니다.
    public static TeamMemberResponse from(TeamMember teamMember) {
        return new TeamMemberResponse(teamMember.getId(), teamMember.getMember().getId(), teamMember.getMember().getName(),
                teamMember.getRoleName(), teamMember.isLeader());
    }
}
