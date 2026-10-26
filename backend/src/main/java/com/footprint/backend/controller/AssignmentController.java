package com.footprint.backend.controller;

import com.footprint.backend.dto.AssignmentCreateRequest;
import com.footprint.backend.dto.AssignmentDetailResponse;
import com.footprint.backend.dto.AssignmentResponse;
import com.footprint.backend.entity.Assignment;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.service.AssignmentService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 과제 요청을 받아 Service를 호출하고, 결과를 응답 DTO로 바꿔 돌려줍니다.
@RestController
@RequestMapping("/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    // 과제와 조, 조장을 한 번에 만듭니다. 교수인지는 Service가 확인합니다.
    @PostMapping
    public AssignmentResponse create(@AuthenticationPrincipal Long memberId,
                                     @Valid @RequestBody AssignmentCreateRequest request) {
        Assignment assignment = assignmentService.create(
                request.title(), request.startDate(), request.endDate(), request.leaderEmails(), memberId);
        return AssignmentResponse.from(assignment);
    }

    // 로그인한 회원이 볼 수 있는 과제 목록을 돌려줍니다.
    @GetMapping
    public List<AssignmentResponse> findAll(@AuthenticationPrincipal Long memberId) {
        return assignmentService.findAllForMember(memberId).stream()
                .map(AssignmentResponse::from)
                .toList();
    }

    // 과제 하나와 볼 수 있는 조 목록을 돌려줍니다. 접근 검사는 Service가 합니다.
    // @PathVariable은 주소의 {assignmentId} 자리에 온 값을 받습니다. 숫자가 아니면 400입니다.
    @GetMapping("/{assignmentId}")
    public AssignmentDetailResponse findById(@AuthenticationPrincipal Long memberId, @PathVariable Long assignmentId) {
        Assignment assignment = assignmentService.findById(assignmentId, memberId);
        List<TeamMember> leaders = assignmentService.findVisibleTeamLeaders(assignment, memberId);
        return AssignmentDetailResponse.from(assignment, leaders);
    }
}
