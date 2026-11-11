package com.footprint.backend.controller;

import com.footprint.backend.dto.NotificationResponse;
import com.footprint.backend.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 알림 요청을 받아 Service를 호출하고, 결과를 응답 DTO로 바꿔 돌려줍니다.
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // 로그인한 회원이 받은 알림을 최근 것부터 돌려줍니다.
    @GetMapping
    public List<NotificationResponse> findMine(@AuthenticationPrincipal Long memberId) {
        return notificationService.findMine(memberId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    // 받은 회원 본인이 알림을 읽음으로 표시하고, 읽은 시각이 담긴 알림을 돌려줍니다.
    @PostMapping("/{notificationId}/read")
    public NotificationResponse read(@AuthenticationPrincipal Long memberId, @PathVariable Long notificationId) {
        return NotificationResponse.from(notificationService.markRead(notificationId, memberId));
    }
}
