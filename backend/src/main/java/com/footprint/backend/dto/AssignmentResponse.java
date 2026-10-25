package com.footprint.backend.dto;

import com.footprint.backend.entity.Assignment;

import java.time.LocalDate;

// 과제를 클라이언트에 돌려줄 때 쓰는 응답 body의 모양입니다. 조 목록은 과제 상세(A-2-2)에서 따로 받습니다.
public record AssignmentResponse(Long id, String title, LocalDate startDate, LocalDate endDate) {

    // 과제 Entity를 응답 DTO로 바꿉니다.
    public static AssignmentResponse from(Assignment assignment) {
        return new AssignmentResponse(assignment.getId(), assignment.getTitle(), assignment.getStartDate(), assignment.getEndDate());
    }
}
