package com.footprint.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// evaluation_draft 테이블과 대응합니다. 평가자 한 명이 대상 조원 한 명에 대해 제출 전에 임시로 저장한 내용이 행 하나입니다.
// 제출본 Evaluation은 점수가 모두 채워져야 해서 따로 둡니다. 제출하면 이 행은 지웁니다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 한 평가자가 한 대상에 임시 저장을 두 벌 갖지 못하도록 (evaluator_id, target_id) 조합에 unique 제약을 둡니다.
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"evaluator_id", "target_id"}))
public class EvaluationDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 저장한 조원의 조 참여 기록입니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluator_id", nullable = false)
    private TeamMember evaluator;

    // 평가 대상 조원의 조 참여 기록입니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    private TeamMember target;

    // 아래 6개 점수는 아직 고르지 않았으면 null입니다. int는 null을 담지 못해서 Integer로 둡니다.
    // 목표 공유·협업
    private Integer goalSharing;

    // 역할 수행
    private Integer roleFulfillment;

    // 격려·도움
    private Integer encouragement;

    // 온라인 소통
    private Integer onlineCommunication;

    // 대면 소통
    private Integer offlineCommunication;

    // 진행 점검·보충
    private Integer progressCheck;

    // 조원별 서술 의견입니다. 비어 있을 수 있습니다.
    @Column(length = 2000)
    private String comment;

    // 저장한 시각입니다. 서버가 기록합니다.
    @Column(nullable = false)
    private LocalDateTime savedAt;

    public EvaluationDraft(TeamMember evaluator, TeamMember target, Integer goalSharing, Integer roleFulfillment,
                           Integer encouragement, Integer onlineCommunication, Integer offlineCommunication,
                           Integer progressCheck, String comment, LocalDateTime savedAt) {
        this.evaluator = evaluator;
        this.target = target;
        this.goalSharing = goalSharing;
        this.roleFulfillment = roleFulfillment;
        this.encouragement = encouragement;
        this.onlineCommunication = onlineCommunication;
        this.offlineCommunication = offlineCommunication;
        this.progressCheck = progressCheck;
        this.comment = comment;
        // 한 번에 저장한 행이 모두 같은 시각을 갖도록 시각은 Service가 한 번 정해서 넘깁니다. 초 아래는 버립니다.
        this.savedAt = savedAt.truncatedTo(ChronoUnit.SECONDS);
    }
}
