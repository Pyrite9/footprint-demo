package com.footprint.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// contribution 테이블과 대응합니다. 파일이 남지 않는 일(회의 진행, 자료 취합 등)의 기록입니다.
// 상태 열을 따로 두지 않습니다. 취소 시각이 있으면 취소됨, 보고 시각이 있으면 보고됨, 둘 다 없으면 보고 전입니다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이 항목이 속한 조입니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    // 담당 조원의 조 참여 기록입니다. 조장 자신일 수도 있고, 역할과는 관계없습니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id", nullable = false)
    private TeamMember assignee;

    // 항목명입니다. 등록한 뒤에는 고치지 않습니다.
    @Column(nullable = false)
    private String title;

    // 등록한 시각입니다. 서버가 기록하고, 사용자가 보낸 시각은 받지 않습니다.
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 완료 보고 메모입니다. 보고 전에는 null입니다. 긴 글이라 varchar(255) 대신 길이 2000으로 둡니다.
    @Column(length = 2000)
    private String reportMemo;

    // 완료 보고 시각입니다. 보고 전에는 null입니다.
    private LocalDateTime reportedAt;

    // 취소 시각입니다. 취소하지 않았으면 null입니다.
    private LocalDateTime canceledAt;

    public Contribution(Team team, TeamMember assignee, String title) {
        this.team = team;
        this.assignee = assignee;
        this.title = title;
        this.createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    // 완료 보고했으면 true입니다.
    public boolean isReported() {
        return reportedAt != null;
    }

    // 취소했으면 true입니다.
    public boolean isCanceled() {
        return canceledAt != null;
    }

    // 취소 시각을 기록합니다. 행은 지우지 않습니다. 시각은 서버가 넣습니다.
    public void cancel() {
        this.canceledAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    // 완료 보고 메모와 시각을 기록합니다. 시각은 서버가 넣습니다.
    public void report(String memo) {
        this.reportMemo = memo;
        this.reportedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
