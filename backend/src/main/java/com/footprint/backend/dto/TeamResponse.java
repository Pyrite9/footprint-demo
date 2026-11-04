package com.footprint.backend.dto;

import com.footprint.backend.entity.AssignmentPhase;
import com.footprint.backend.entity.Team;

// 조 정보 응답의 모양입니다. 조가 속한 과제의 번호, 이름, 단계를 펼쳐서 담습니다.
public record TeamResponse(Long id, String name, Long assignmentId, String assignmentTitle, AssignmentPhase phase) {

    // 조 Entity를 응답 DTO로 바꿉니다.
    public static TeamResponse from(Team team) {
        return new TeamResponse(team.getId(), team.getName(), team.getAssignment().getId(), team.getAssignment().getTitle(),
                team.getAssignment().getPhase());
    }
}
