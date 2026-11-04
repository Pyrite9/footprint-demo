package com.footprint.backend.dto;

import com.footprint.backend.entity.Evaluation;

import java.time.LocalDateTime;

// 평가 결과의 한 줄입니다. 평가자 한 명이 대상 한 명에게 입력한 점수와 의견을 그대로 담습니다. 합계, 평균, 순위는 담지 않습니다. comment는 비었으면 null입니다.
public record EvaluationResultResponse(Long id, Long evaluatorTeamMemberId, String evaluatorName,
                                       Long targetTeamMemberId, String targetName,
                                       int goalSharing, int roleFulfillment, int encouragement,
                                       int onlineCommunication, int offlineCommunication, int progressCheck,
                                       String comment, LocalDateTime submittedAt) {

    // 평가 Entity를 응답 DTO로 바꿉니다.
    public static EvaluationResultResponse from(Evaluation evaluation) {
        return new EvaluationResultResponse(evaluation.getId(),
                evaluation.getEvaluator().getId(), evaluation.getEvaluator().getMember().getName(),
                evaluation.getTarget().getId(), evaluation.getTarget().getMember().getName(),
                evaluation.getGoalSharing(), evaluation.getRoleFulfillment(), evaluation.getEncouragement(),
                evaluation.getOnlineCommunication(), evaluation.getOfflineCommunication(), evaluation.getProgressCheck(),
                evaluation.getComment(), evaluation.getSubmittedAt());
    }
}
