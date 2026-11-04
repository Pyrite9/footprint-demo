package com.footprint.backend.controller;

import com.footprint.backend.dto.EvaluationCsv;
import com.footprint.backend.dto.EvaluationDraftResponse;
import com.footprint.backend.dto.EvaluationDraftSaveRequest;
import com.footprint.backend.dto.EvaluationResultResponse;
import com.footprint.backend.dto.EvaluationStatusResponse;
import com.footprint.backend.dto.EvaluationSubmissionResponse;
import com.footprint.backend.dto.EvaluationSubmitRequest;
import com.footprint.backend.service.EvaluationService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

// 동료평가 요청을 받아 Service를 호출합니다. 조원에게는 제출한 평가 내용을 돌려주지 않고 제출 여부만 돌려줍니다. 임시 저장은 본인에게만 돌려줍니다.
// 제출된 평가 내용과 조원별 제출 여부는 과제를 만든 교수만 받습니다.
@RestController
@RequestMapping("/teams/{teamId}/evaluations")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    // 조원별 제출 여부를 돌려줍니다. 몇 명이 제출했는지는 세지 않습니다.
    @GetMapping("/status")
    public List<EvaluationSubmissionResponse> findSubmissions(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId) {
        return EvaluationSubmissionResponse.listFrom(evaluationService.findSubmissions(teamId, memberId));
    }

    // 평가자별 점수와 의견을 입력된 그대로 돌려줍니다.
    @GetMapping
    public List<EvaluationResultResponse> findAll(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId) {
        return evaluationService.findTeamEvaluations(teamId, memberId).stream()
                .map(EvaluationResultResponse::from)
                .toList();
    }

    // 평가 결과를 CSV 파일로 내려줍니다. JSON이 아니라 파일 내용을 body로 보내므로 ResponseEntity를 씁니다.
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId) {
        byte[] csv = EvaluationCsv.from(evaluationService.findTeamEvaluations(teamId, memberId));
        // attachment: 브라우저가 열지 않고 내려받습니다.
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("evaluations-team-" + teamId + ".csv")
                .build();
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(csv);
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
