package com.footprint.backend.dto;

import com.footprint.backend.entity.Notification;
import com.footprint.backend.entity.NotificationType;

import java.time.LocalDateTime;

// 알림 한 건의 모양입니다. 점수, 평가 의견, 건수는 담지 않습니다.
// teamId: 화면이 알림을 누르면 갈 조 화면을 정할 때 씁니다.
public record NotificationResponse(Long id, NotificationType type, String message, Long teamId,
                                   LocalDateTime createdAt, LocalDateTime readAt) {

    // 알림 Entity를 응답 DTO로 바꿉니다.
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getMessage(),
                notification.getTeam().getId(), notification.getCreatedAt(), notification.getReadAt());
    }
}
