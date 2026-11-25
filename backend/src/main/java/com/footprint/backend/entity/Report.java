package com.footprint.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// report 테이블과 대응합니다. 조장이 만든 보고서 하나이고, 글은 파트(ReportPart)로 나눠 씁니다.
// 한 조에 여러 개를 둘 수 있습니다. 제목은 고치지 않고, 보고서는 지우지 않습니다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이 보고서가 속한 조입니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    // 보고서 제목입니다. 만든 뒤에는 고치지 않습니다.
    @Column(nullable = false)
    private String title;

    // 만든 시각입니다. 서버가 기록하고, 사용자가 보낸 시각은 받지 않습니다.
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Report(Team team, String title) {
        this.team = team;
        this.title = title;
        this.createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
