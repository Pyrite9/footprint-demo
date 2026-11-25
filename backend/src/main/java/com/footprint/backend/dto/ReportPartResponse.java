package com.footprint.backend.dto;

import com.footprint.backend.entity.ReportPart;

import java.time.LocalDateTime;

// 보고서 파트 한 줄의 모양입니다. 담당 조원은 화면에 바로 쓰도록 회원 번호와 이름으로 펼칩니다.
// assigneeMemberId: 화면이 "내가 맡은 파트"를 알아볼 때 씁니다.
public record ReportPartResponse(Long id, String title, Long assigneeMemberId, String assigneeName,
                                 LocalDateTime createdAt, LocalDateTime canceledAt) {

    // 보고서 파트 Entity를 응답 DTO로 바꿉니다.
    public static ReportPartResponse from(ReportPart part) {
        return new ReportPartResponse(part.getId(), part.getTitle(),
                part.getAssignee().getMember().getId(), part.getAssignee().getMember().getName(),
                part.getCreatedAt(), part.getCanceledAt());
    }
}
