package com.footprint.backend.dto;

import com.footprint.backend.entity.Confirmation;

import java.time.LocalDateTime;

// 근거 패널의 완료확인 한 건입니다. 화면이 어떤 기록의 확인인지 보여 주고 그 기록을 바로 열 수 있도록 기록을 함께 담습니다.
// deliverable과 contribution은 둘 중 하나만 값이 있고 나머지는 null입니다. canceledAt은 취소하지 않았으면 null입니다.
public record EvidenceConfirmationResponse(Long id, DeliverableResponse deliverable, ContributionResponse contribution,
                                           Long confirmerMemberId, String confirmerName, LocalDateTime confirmedAt,
                                           LocalDateTime canceledAt) {

    // 완료확인 Entity를 응답 DTO로 바꿉니다.
    public static EvidenceConfirmationResponse from(Confirmation confirmation) {
        DeliverableResponse deliverable = confirmation.getDeliverable() == null ? null : DeliverableResponse.from(confirmation.getDeliverable());
        ContributionResponse contribution = confirmation.getContribution() == null ? null : ContributionResponse.from(confirmation.getContribution());
        return new EvidenceConfirmationResponse(confirmation.getId(), deliverable, contribution,
                confirmation.getConfirmer().getMember().getId(), confirmation.getConfirmer().getMember().getName(),
                confirmation.getConfirmedAt(), confirmation.getCanceledAt());
    }
}
