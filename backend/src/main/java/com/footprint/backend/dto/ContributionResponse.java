package com.footprint.backend.dto;

import com.footprint.backend.entity.Contribution;

import java.time.LocalDateTime;

// 기여 항목 한 줄의 모양입니다. 담당 조원은 화면에 바로 쓰도록 회원 번호와 이름으로 펼칩니다.
// assigneeMemberId: 화면이 "내가 맡은 항목"을 알아볼 때 씁니다.
public record ContributionResponse(Long id, String title, Long assigneeMemberId, String assigneeName,
                                   LocalDateTime createdAt, String reportMemo, LocalDateTime reportedAt,
                                   LocalDateTime canceledAt) {

    // 기여 항목 Entity를 응답 DTO로 바꿉니다.
    public static ContributionResponse from(Contribution contribution) {
        return new ContributionResponse(contribution.getId(), contribution.getTitle(),
                contribution.getAssignee().getMember().getId(), contribution.getAssignee().getMember().getName(),
                contribution.getCreatedAt(), contribution.getReportMemo(), contribution.getReportedAt(),
                contribution.getCanceledAt());
    }
}
