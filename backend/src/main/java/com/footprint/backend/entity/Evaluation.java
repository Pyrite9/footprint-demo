package com.footprint.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// evaluation 테이블과 대응합니다. 평가자 한 명이 대상 조원 한 명을 평가한 기록이 행 하나입니다.
// 6개 항목 점수(1~5)를 열로 저장합니다. 시스템은 이 점수를 합치거나 평균 내지 않습니다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 같은 평가자가 같은 대상을 두 번 평가하지 못하도록 (evaluator_id, target_id) 조합에 unique 제약을 둡니다.
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"evaluator_id", "target_id"}))
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 평가한 조원의 조 참여 기록입니다. 회원이 아니라 조 참여를 참조합니다(같은 회원도 조마다 다른 조원이기 때문입니다).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluator_id", nullable = false)
    private TeamMember evaluator;

    // 평가받은 조원의 조 참여 기록입니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    private TeamMember target;

    // 목표 공유·협업
    @Column(nullable = false)
    private int goalSharing;

    // 역할 수행
    @Column(nullable = false)
    private int roleFulfillment;

    // 격려·도움
    @Column(nullable = false)
    private int encouragement;

    // 온라인 소통
    @Column(nullable = false)
    private int onlineCommunication;

    // 대면 소통
    @Column(nullable = false)
    private int offlineCommunication;

    // 진행 점검·보충
    @Column(nullable = false)
    private int progressCheck;

    // 조원별 서술 의견입니다. 선택이라 비어 있을 수 있습니다. 긴 글이라 varchar(255) 대신 길이 2000으로 둡니다.
    @Column(length = 2000)
    private String comment;

    // 제출한 시각입니다. 서버가 기록합니다.
    @Column(nullable = false)
    private LocalDateTime submittedAt;

    public Evaluation(TeamMember evaluator, TeamMember target, int goalSharing, int roleFulfillment, int encouragement,
                      int onlineCommunication, int offlineCommunication, int progressCheck, String comment,
                      LocalDateTime submittedAt) {
        this.evaluator = evaluator;
        this.target = target;
        this.goalSharing = goalSharing;
        this.roleFulfillment = roleFulfillment;
        this.encouragement = encouragement;
        this.onlineCommunication = onlineCommunication;
        this.offlineCommunication = offlineCommunication;
        this.progressCheck = progressCheck;
        this.comment = comment;
        // 한 번에 제출한 평가가 모두 같은 시각을 갖도록 시각은 Service가 한 번 정해서 넘깁니다. 초 아래는 버립니다.
        this.submittedAt = submittedAt.truncatedTo(ChronoUnit.SECONDS);
    }
}
