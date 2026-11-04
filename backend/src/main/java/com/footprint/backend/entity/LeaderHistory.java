package com.footprint.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// leader_history 테이블과 대응합니다. 조장이 바뀔 때마다 이전 조장, 새 조장, 바뀐 시각을 한 줄씩 남깁니다.
// 만든 뒤에는 고치거나 지우지 않습니다. 그래서 값을 바꾸는 메서드가 없습니다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaderHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 조장이 바뀐 조입니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    // 이전 조장의 조 참여 기록입니다. 같은 테이블을 두 번 가리켜서 열 이름을 따로 정합니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_leader_id", nullable = false)
    private TeamMember previousLeader;

    // 새 조장의 조 참여 기록입니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_leader_id", nullable = false)
    private TeamMember newLeader;

    // 조장이 바뀐 시각입니다. 서버가 기록하고, 사용자가 보낸 시각은 받지 않습니다.
    @Column(nullable = false)
    private LocalDateTime changedAt;

    public LeaderHistory(Team team, TeamMember previousLeader, TeamMember newLeader) {
        this.team = team;
        this.previousLeader = previousLeader;
        this.newLeader = newLeader;
        this.changedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
