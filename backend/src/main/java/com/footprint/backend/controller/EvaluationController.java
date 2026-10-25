package com.footprint.backend.controller;

import com.footprint.backend.dto.EvaluationStatusResponse;
import com.footprint.backend.dto.EvaluationSubmitRequest;
import com.footprint.backend.service.EvaluationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 동료평가 요청을 받아 Service를 호출합니다. 평가 내용은 돌려주지 않고 제출 여부만 돌려줍니다.
@RestController
@RequestMapping("/teams/{teamId}/evaluations")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    // 내가 이 조에서 동료평가를 제출했는지 돌려줍니다.
    @GetMapping("/mine")
    public EvaluationStatusResponse findMine(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId) {
        return EvaluationStatusResponse.from(evaluationService.findMine(teamId, memberId));
    }

    // 자신을 뺀 조원 전원의 평가를 한 번에 제출하고 제출 여부를 돌려줍니다.
    @PostMapping
    public EvaluationStatusResponse submit(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId,
                                           @Valid @RequestBody EvaluationSubmitRequest request) {
        return EvaluationStatusResponse.from(evaluationService.submit(teamId, request.evaluations(), memberId));
    }
}
