package com.footprint.backend.controller;

import com.footprint.backend.dto.EvaluationDraftResponse;
import com.footprint.backend.dto.EvaluationDraftSaveRequest;
import com.footprint.backend.dto.EvaluationStatusResponse;
import com.footprint.backend.dto.EvaluationSubmitRequest;
import com.footprint.backend.service.EvaluationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 동료평가 요청을 받아 Service를 호출합니다. 제출한 평가 내용은 돌려주지 않고 제출 여부만 돌려줍니다. 임시 저장은 본인에게만 돌려줍니다.
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

    // 내가 이 조에서 임시 저장한 내용을 돌려줍니다. 없으면 빈 목록입니다.
    @GetMapping("/draft")
    public List<EvaluationDraftResponse> findMyDrafts(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId) {
        return evaluationService.findMyDrafts(teamId, memberId).stream()
                .map(EvaluationDraftResponse::from)
                .toList();
    }

    // 지금까지 입력한 내용을 임시 저장하고 저장한 내용을 돌려줍니다. 다시 저장하면 마지막 내용으로 바뀝니다.
    @PutMapping("/draft")
    public List<EvaluationDraftResponse> saveDrafts(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId,
                                                    @Valid @RequestBody EvaluationDraftSaveRequest request) {
        return evaluationService.saveDrafts(teamId, request.evaluations(), memberId).stream()
                .map(EvaluationDraftResponse::from)
                .toList();
    }

    // 자신을 뺀 조원 전원의 평가를 한 번에 제출하고 제출 여부를 돌려줍니다.
    @PostMapping
    public EvaluationStatusResponse submit(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId,
                                           @Valid @RequestBody EvaluationSubmitRequest request) {
        return EvaluationStatusResponse.from(evaluationService.submit(teamId, request.evaluations(), memberId));
    }
}
