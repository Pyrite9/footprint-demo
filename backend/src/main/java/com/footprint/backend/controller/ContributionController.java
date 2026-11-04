package com.footprint.backend.controller;

import com.footprint.backend.dto.ContributionCreateRequest;
import com.footprint.backend.dto.ContributionReportRequest;
import com.footprint.backend.dto.ContributionResponse;
import com.footprint.backend.service.ContributionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 기여 항목 요청을 받아 Service를 호출합니다. 주소가 /teams/..와 /contributions/.. 두 갈래라서 클래스에 @RequestMapping을 두지 않습니다.
@RestController
public class ContributionController {

    private final ContributionService contributionService;

    public ContributionController(ContributionService contributionService) {
        this.contributionService = contributionService;
    }

    // 조장이 기여 항목을 등록하고 저장된 항목을 돌려줍니다.
    @PostMapping("/teams/{teamId}/contributions")
    public ContributionResponse create(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId,
                                       @Valid @RequestBody ContributionCreateRequest request) {
        return ContributionResponse.from(contributionService.create(teamId, request.title(), request.teamMemberId(), memberId));
    }

    // 조의 기여 항목 목록을 최근에 등록한 것부터 돌려줍니다.
    @GetMapping("/teams/{teamId}/contributions")
    public List<ContributionResponse> findAll(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId) {
        return contributionService.findAll(teamId, memberId).stream()
                .map(ContributionResponse::from)
                .toList();
    }

    // 담당 조원이 완료 보고하고, 메모와 보고 시각이 담긴 항목을 돌려줍니다.
    @PostMapping("/contributions/{contributionId}/report")
    public ContributionResponse report(@AuthenticationPrincipal Long memberId, @PathVariable Long contributionId,
                                       @Valid @RequestBody ContributionReportRequest request) {
        return ContributionResponse.from(contributionService.report(contributionId, request.memo(), memberId));
    }
}
