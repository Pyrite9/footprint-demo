package com.footprint.backend.dto;

import com.footprint.backend.entity.Assignment;
import com.footprint.backend.entity.TeamMember;

import java.time.LocalDate;
import java.util.List;

// 과제 상세 응답의 모양입니다. 과제 정보와 교수 이름, 볼 수 있는 조 목록을 함께 담습니다.
public record AssignmentDetailResponse(Long id, String title, LocalDate startDate, LocalDate endDate,
                                       String professorName, List<TeamSummaryResponse> teams) {

    // 과제 Entity와 조장 기록 목록을 응답 DTO로 바꿉니다.
    public static AssignmentDetailResponse from(Assignment assignment, List<TeamMember> leaders) {
        List<TeamSummaryResponse> teams = leaders.stream()
                .map(TeamSummaryResponse::from)
                .toList();
        return new AssignmentDetailResponse(assignment.getId(), assignment.getTitle(), assignment.getStartDate(),
                assignment.getEndDate(), assignment.getProfessor().getName(), teams);
    }
}
