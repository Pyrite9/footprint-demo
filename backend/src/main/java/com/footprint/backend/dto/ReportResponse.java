package com.footprint.backend.dto;

import com.footprint.backend.entity.Report;

import java.time.LocalDateTime;

// 보고서 한 줄의 모양입니다. 파트는 따로 불러옵니다(GET /reports/{reportId}/parts).
public record ReportResponse(Long id, String title, LocalDateTime createdAt) {

    // 보고서 Entity를 응답 DTO로 바꿉니다.
    public static ReportResponse from(Report report) {
        return new ReportResponse(report.getId(), report.getTitle(), report.getCreatedAt());
    }
}
