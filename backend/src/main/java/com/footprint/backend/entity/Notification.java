package com.footprint.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// notification 테이블과 대응합니다. 화면 안 알림 한 건이고, 받는 회원마다 한 줄씩 저장합니다.
// 점수와 평가 의견은 담지 않습니다. 알림은 지우지 않고, 읽은 시각만 채웁니다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 알림을 받는 회원입니다. 조 참여가 아니라 회원을 가리켜서 여러 과제의 알림을 한 목록으로 찾습니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Member recipient;

    // enum을 이름(EVALUATION_STARTED 등)으로 저장합니다. 이유는 Member.memberType과 같습니다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    // 화면에 보일 문장입니다. 과제 제목과 항목명이 각각 255자까지라서 길이를 1000으로 둡니다.
    @Column(nullable = false, length = 1000)
    private String message;

    // 알림이 생긴 조입니다. 화면이 알림을 누르면 이 조의 화면으로 갑니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    // 알림을 만든 시각입니다. 서버가 기록합니다.
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 받은 회원이 읽은 시각입니다. 읽기 전에는 null입니다.
    private LocalDateTime readAt;

    public Notification(Member recipient, NotificationType type, String message, Team team) {
        this.recipient = recipient;
        this.type = type;
        this.message = message;
        this.team = team;
        this.createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    // 읽었으면 true입니다.
    public boolean isRead() {
        return readAt != null;
    }

    // 읽은 시각을 기록합니다. 시각은 서버가 넣습니다.
    public void markRead() {
        this.readAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
