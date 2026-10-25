package com.footprint.backend.dto;

import com.footprint.backend.entity.Evaluation;

import java.time.LocalDateTime;
import java.util.List;

// 내 동료평가 제출 여부의 모양입니다. 점수는 담지 않습니다.
public record EvaluationStatusResponse(boolean submitted, LocalDateTime submittedAt) {

    // 내가 제출한 평가 목록을 제출 여부로 바꿉니다. 비어 있으면 제출 전입니다. 한 번에 제출해서 시각은 모두 같습니다.
    public static EvaluationStatusResponse from(List<Evaluation> myEvaluations) {
        if (myEvaluations.isEmpty()) {
            return new EvaluationStatusResponse(false, null);
        }
        return new EvaluationStatusResponse(true, myEvaluations.get(0).getSubmittedAt());
    }
}
