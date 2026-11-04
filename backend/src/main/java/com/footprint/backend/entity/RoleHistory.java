package com.footprint.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// role_history 테이블과 대응합니다. 역할이 바뀔 때마다 이전 역할명과 바뀐 시각을 한 줄씩 남깁니다.
// 만든 뒤에는 고치거나 지우지 않습니다. 그래서 값을 바꾸는 메서드가 없습니다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 역할이 바뀐 조원의 조 참여 기록입니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_member_id", nullable = false)
    private TeamMember teamMember;

    // 바뀌기 전의 역할명입니다.
    @Column(nullable = false)
    private String previousRoleName;

    // 역할이 바뀐 시각입니다. 서버가 기록하고, 사용자가 보낸 시각은 받지 않습니다.
    @Column(nullable = false)
    private LocalDateTime changedAt;

    public RoleHistory(TeamMember teamMember, String previousRoleName) {
        this.teamMember = teamMember;
        this.previousRoleName = previousRoleName;
        this.changedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
