package com.footprint.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// report_part 테이블과 대응합니다. 보고서의 한 부분이고, 담당 조원 한 명이 씁니다.
// 상태 열을 따로 두지 않습니다. 취소 시각이 있으면 취소됨입니다. 취소한 파트는 합본에서 빠지지만 행은 남습니다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이 파트가 속한 보고서입니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    // 담당 조원의 조 참여 기록입니다. 조장 자신일 수도 있고, 역할과는 관계없습니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id", nullable = false)
    private TeamMember assignee;

    // 파트 제목입니다. 등록한 뒤에는 고치지 않습니다.
    @Column(nullable = false)
    private String title;

    // 등록한 시각입니다. 서버가 기록하고, 사용자가 보낸 시각은 받지 않습니다.
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 취소 시각입니다. 취소하지 않았으면 null입니다.
    private LocalDateTime canceledAt;

    public ReportPart(Report report, TeamMember assignee, String title) {
        this.report = report;
        this.assignee = assignee;
        this.title = title;
        this.createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    // 취소했으면 true입니다.
    public boolean isCanceled() {
        return canceledAt != null;
    }

    // 취소 시각을 기록합니다. 행은 지우지 않습니다. 시각은 서버가 넣습니다.
    public void cancel() {
        this.canceledAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
