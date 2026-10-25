package com.footprint.backend.controller;

import com.footprint.backend.dto.AssignmentCreateRequest;
import com.footprint.backend.dto.AssignmentResponse;
import com.footprint.backend.entity.Assignment;
import com.footprint.backend.service.AssignmentService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
                memberId, request.title(), request.startDate(), request.endDate(), request.leaderEmails());
        return AssignmentResponse.from(assignment);
    }
}
