package com.footprint.backend.controller;

import com.footprint.backend.dto.ConfirmationResponse;
import com.footprint.backend.service.ConfirmationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 완료확인 요청을 받아 Service를 호출합니다. 주소가 /deliverables/..와 /contributions/.. 두 갈래라서 클래스에 @RequestMapping을 두지 않습니다.
@RestController
public class ConfirmationController {

    private final ConfirmationService confirmationService;

    public ConfirmationController(ConfirmationService confirmationService) {
        this.confirmationService = confirmationService;
    }

    // 산출물을 확인하고 저장된 확인을 돌려줍니다.
    @PostMapping("/deliverables/{deliverableId}/confirmations")
    public ConfirmationResponse confirmDeliverable(@AuthenticationPrincipal Long memberId, @PathVariable Long deliverableId) {
        return ConfirmationResponse.from(confirmationService.confirmDeliverable(deliverableId, memberId));
    }

    // 보고된 기여 항목을 확인하고 저장된 확인을 돌려줍니다.
    @PostMapping("/contributions/{contributionId}/confirmations")
    public ConfirmationResponse confirmContribution(@AuthenticationPrincipal Long memberId, @PathVariable Long contributionId) {
        return ConfirmationResponse.from(confirmationService.confirmContribution(contributionId, memberId));
    }

    // 산출물의 완료확인 이력을 최근 것부터 돌려줍니다.
    @GetMapping("/deliverables/{deliverableId}/confirmations")
    public List<ConfirmationResponse> findForDeliverable(@AuthenticationPrincipal Long memberId, @PathVariable Long deliverableId) {
        return confirmationService.findForDeliverable(deliverableId, memberId).stream()
                .map(ConfirmationResponse::from)
                .toList();
    }

    // 기여 항목의 완료확인 이력을 최근 것부터 돌려줍니다.
    @GetMapping("/contributions/{contributionId}/confirmations")
    public List<ConfirmationResponse> findForContribution(@AuthenticationPrincipal Long memberId, @PathVariable Long contributionId) {
        return confirmationService.findForContribution(contributionId, memberId).stream()
                .map(ConfirmationResponse::from)
                .toList();
    }
}
