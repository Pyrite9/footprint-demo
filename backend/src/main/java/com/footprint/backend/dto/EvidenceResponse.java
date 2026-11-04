package com.footprint.backend.dto;

import com.footprint.backend.service.TeamMemberEvidence;

import java.util.List;

// 근거 패널에 보여 줄 한 조원의 기록을 종류별로 담습니다. 건수나 점수는 담지 않습니다.
public record EvidenceResponse(List<DeliverableResponse> deliverables, List<ContributionResponse> contributions,
                               List<EvidenceConfirmationResponse> receivedConfirmations,
                               List<EvidenceConfirmationResponse> givenConfirmations) {

    // Service가 모은 Entity 묶음을 응답 DTO로 바꿉니다.
    public static EvidenceResponse from(TeamMemberEvidence evidence) {
        return new EvidenceResponse(
                evidence.deliverables().stream().map(DeliverableResponse::from).toList(),
                evidence.contributions().stream().map(ContributionResponse::from).toList(),
                evidence.receivedConfirmations().stream().map(EvidenceConfirmationResponse::from).toList(),
                evidence.givenConfirmations().stream().map(EvidenceConfirmationResponse::from).toList());
    }
}
