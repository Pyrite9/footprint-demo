package com.footprint.backend.dto;

import com.footprint.backend.entity.Evaluation;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.service.TeamEvaluations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 제출 현황의 한 줄입니다. 조원 한 명이 동료평가를 제출했는지 담습니다. 몇 명이 제출했는지는 세지 않습니다. submittedAt은 제출 전이면 null입니다.
public record EvaluationSubmissionResponse(Long teamMemberId, String name, boolean submitted, LocalDateTime submittedAt) {

    // 조원 전원을 한 줄씩 펼칩니다. 임시 저장은 evaluations에 없어서 미제출로 나옵니다.
    public static List<EvaluationSubmissionResponse> listFrom(TeamEvaluations teamEvaluations) {
        List<EvaluationSubmissionResponse> rows = new ArrayList<>();
        for (TeamMember teamMember : teamEvaluations.teamMembers()) {
            rows.add(from(teamMember, teamEvaluations.evaluations()));
        }
        return rows;
    }

    // 조원 한 명의 줄을 만듭니다. 한 번에 제출해서 시각은 모두 같으므로 처음 찾은 평가의 시각을 씁니다.
    private static EvaluationSubmissionResponse from(TeamMember teamMember, List<Evaluation> evaluations) {
        for (Evaluation evaluation : evaluations) {
            if (evaluation.getEvaluator().getId().equals(teamMember.getId())) {
                return new EvaluationSubmissionResponse(teamMember.getId(), teamMember.getMember().getName(), true, evaluation.getSubmittedAt());
            }
        }
        return new EvaluationSubmissionResponse(teamMember.getId(), teamMember.getMember().getName(), false, null);
    }
}
