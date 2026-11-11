package com.footprint.backend.dto;

import com.footprint.backend.entity.TeamMember;

// 합류 전 미리보기 응답의 모양입니다. 과제 제목, 조 이름, 조장 이름만 담습니다.
// 초대 코드를 아는 사람에게 필요한 최소만 줍니다. 조원 수, 조원 목록, 조 번호는 담지 않습니다.
public record TeamJoinPreviewResponse(String assignmentTitle, String teamName, String leaderName) {

    // 조장의 조 참여 기록 하나를 미리보기로 바꿉니다. 과제와 조는 leader.getTeam()에서, 조장 이름은 leader.getMember()에서 꺼냅니다.
    public static TeamJoinPreviewResponse from(TeamMember leader) {
        return new TeamJoinPreviewResponse(leader.getTeam().getAssignment().getTitle(), leader.getTeam().getName(),
                leader.getMember().getName());
    }
}
