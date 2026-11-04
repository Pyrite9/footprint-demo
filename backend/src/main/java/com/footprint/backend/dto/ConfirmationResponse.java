package com.footprint.backend.dto;

import com.footprint.backend.entity.Confirmation;

import java.time.LocalDateTime;

// 완료확인 한 건의 모양입니다. 확인한 조원은 화면에 바로 쓰도록 회원 번호와 이름으로 펼칩니다.
// deliverableId와 contributionId는 둘 중 하나만 값이 있고 나머지는 null입니다. canceledAt은 취소하지 않았으면 null입니다.
public record ConfirmationResponse(Long id, Long deliverableId, Long contributionId, Long confirmerMemberId,
                                   String confirmerName, LocalDateTime confirmedAt, LocalDateTime canceledAt) {

    // 완료확인 Entity를 응답 DTO로 바꿉니다.
    public static ConfirmationResponse from(Confirmation confirmation) {
        Long deliverableId = confirmation.getDeliverable() == null ? null : confirmation.getDeliverable().getId();
        Long contributionId = confirmation.getContribution() == null ? null : confirmation.getContribution().getId();
        return new ConfirmationResponse(confirmation.getId(), deliverableId, contributionId,
                confirmation.getConfirmer().getMember().getId(), confirmation.getConfirmer().getMember().getName(),
                confirmation.getConfirmedAt(), confirmation.getCanceledAt());
    }
}
