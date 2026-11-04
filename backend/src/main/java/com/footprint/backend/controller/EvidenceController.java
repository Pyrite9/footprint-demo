package com.footprint.backend.controller;

import com.footprint.backend.dto.EvidenceResponse;
import com.footprint.backend.service.EvidenceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// 근거 패널 요청을 받아 Service를 호출합니다.
@RestController
public class EvidenceController {

    private final EvidenceService evidenceService;

    public EvidenceController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    // 한 조원의 기록을 종류별로 돌려줍니다.
    @GetMapping("/teams/{teamId}/members/{teamMemberId}/evidence")
    public EvidenceResponse find(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId, @PathVariable Long teamMemberId) {
        return EvidenceResponse.from(evidenceService.find(teamId, teamMemberId, memberId));
    }
}
