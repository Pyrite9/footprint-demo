package com.footprint.backend.controller;

import com.footprint.backend.dto.ReportCreateRequest;
import com.footprint.backend.dto.ReportPartCreateRequest;
import com.footprint.backend.dto.ReportPartResponse;
import com.footprint.backend.dto.ReportResponse;
import com.footprint.backend.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 보고서와 파트 요청을 받아 Service를 호출합니다. 주소가 /teams/.., /reports/.., /report-parts/.. 세 갈래라서 클래스에 @RequestMapping을 두지 않습니다.
@RestController
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    // 조장이 보고서를 만들고 저장된 보고서를 돌려줍니다.
    @PostMapping("/teams/{teamId}/reports")
    public ReportResponse create(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId,
                                 @Valid @RequestBody ReportCreateRequest request) {
        return ReportResponse.from(reportService.create(teamId, request.title(), memberId));
    }

    // 조의 보고서 목록을 최근에 만든 것부터 돌려줍니다.
    @GetMapping("/teams/{teamId}/reports")
    public List<ReportResponse> findAll(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId) {
        return reportService.findAll(teamId, memberId).stream()
                .map(ReportResponse::from)
                .toList();
    }

    // 조장이 파트를 등록하고 저장된 파트를 돌려줍니다.
    @PostMapping("/reports/{reportId}/parts")
    public ReportPartResponse createPart(@AuthenticationPrincipal Long memberId, @PathVariable Long reportId,
                                         @Valid @RequestBody ReportPartCreateRequest request) {
        return ReportPartResponse.from(reportService.createPart(reportId, request.title(), request.teamMemberId(), memberId));
    }

    // 보고서의 파트 목록을 먼저 등록한 것부터 돌려줍니다.
    @GetMapping("/reports/{reportId}/parts")
    public List<ReportPartResponse> findParts(@AuthenticationPrincipal Long memberId, @PathVariable Long reportId) {
        return reportService.findParts(reportId, memberId).stream()
                .map(ReportPartResponse::from)
                .toList();
    }

    // 조장이 파트를 취소하고, 취소 시각이 담긴 파트를 돌려줍니다.
    @PostMapping("/report-parts/{partId}/cancel")
    public ReportPartResponse cancelPart(@AuthenticationPrincipal Long memberId, @PathVariable Long partId) {
        return ReportPartResponse.from(reportService.cancelPart(partId, memberId));
    }
}
