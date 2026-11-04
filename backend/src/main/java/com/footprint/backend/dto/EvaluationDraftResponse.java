package com.footprint.backend.dto;

import com.footprint.backend.entity.EvaluationDraft;

import java.time.LocalDateTime;

// 대상 조원 한 명의 임시 저장을 돌려줄 때의 모양입니다. 아직 고르지 않은 점수는 null입니다. 본인에게만 돌려줍니다.
public record EvaluationDraftResponse(
    Long targetTeamMemberId,
    Integer goalSharing,
    Integer roleFulfillment,
    Integer encouragement,
    Integer onlineCommunication,
    Integer offlineCommunication,
    Integer progressCheck,
    String comment,
    LocalDateTime savedAt
) {

    // Entity를 응답 DTO로 바꿉니다.
    public static EvaluationDraftResponse from(EvaluationDraft draft) {
        return new EvaluationDraftResponse(
                draft.getTarget().getId(),
                draft.getGoalSharing(),
                draft.getRoleFulfillment(),
                draft.getEncouragement(),
                draft.getOnlineCommunication(),
                draft.getOfflineCommunication(),
                draft.getProgressCheck(),
                draft.getComment(),
                draft.getSavedAt());
    }
}
