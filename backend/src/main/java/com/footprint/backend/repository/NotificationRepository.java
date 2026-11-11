package com.footprint.backend.repository;

import com.footprint.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// notification 테이블을 저장·조회합니다.
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 한 회원이 받은 알림을 최근 것부터 찾습니다. RecipientId는 recipient.id입니다.
    List<Notification> findByRecipientIdOrderByIdDesc(Long recipientId);
}
