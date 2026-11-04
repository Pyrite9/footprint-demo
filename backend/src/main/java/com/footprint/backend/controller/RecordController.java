package com.footprint.backend.controller;

import com.footprint.backend.dto.RecordResponse;
import com.footprint.backend.service.RecordService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 기록 열람 요청을 받아 Service를 호출합니다.
@RestController
public class RecordController {

    private final RecordService recordService;

    public RecordController(RecordService recordService) {
        this.recordService = recordService;
    }

    // 한 조의 기록을 최근 것부터 돌려줍니다.
    @GetMapping("/teams/{teamId}/records")
    public List<RecordResponse> findAll(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId) {
        return RecordResponse.listFrom(recordService.find(teamId, memberId));
    }
}
