package com.footprint.backend.service;

import com.footprint.backend.entity.Confirmation;
import com.footprint.backend.entity.Contribution;
import com.footprint.backend.entity.Deliverable;

import java.util.List;

// EvidenceService가 모은 한 조원의 기록 묶음입니다. 종류가 넷이라 Entity 목록을 한 값으로 묶어 돌려줍니다. 응답 모양은 EvidenceResponse입니다.
// receivedConfirmations: 이 조원의 기록에 다른 조원이 남긴 확인. givenConfirmations: 이 조원이 다른 조원의 기록에 남긴 확인.
public record TeamMemberEvidence(List<Deliverable> deliverables, List<Contribution> contributions,
                                 List<Confirmation> receivedConfirmations, List<Confirmation> givenConfirmations) {
}
