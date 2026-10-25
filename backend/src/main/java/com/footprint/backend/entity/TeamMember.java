package com.footprint.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// team_member 테이블과 대응합니다. "어느 회원이 어느 조에 어떤 역할로 있는지"를 나타내는 조 참여 기록입니다.
// 산출물과 평가는 회원이 아니라 이 조 참여를 참조합니다. 조 단위 권한 검사의 기준입니다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 같은 회원이 같은 조에 두 번 등록되지 않도록 (team_id, member_id) 조합에 unique 제약을 둡니다.
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "member_id"}))
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // 조장이면 true입니다. 조장도 이 조의 조원으로 함께 등록됩니다. Lombok이 만드는 getter 이름은 isLeader()입니다.
    @Column(nullable = false)
    private boolean leader;

    // 조장이 정한 역할명입니다. 역할을 배정하기 전에는 비어 있습니다.
    private String roleName;

    // 역할을 지정한 시각입니다. 서버가 기록하고, 사용자가 보낸 시각은 받지 않습니다.
    private LocalDateTime roleAssignedAt;

    public TeamMember(Team team, Member member, boolean leader) {
        this.team = team;
        this.member = member;
        this.leader = leader;
    }

    // 역할을 지정합니다. 지정 시각은 서버의 현재 시각으로 기록합니다.
    public void assignRole(String roleName) {
        this.roleName = roleName;
        this.roleAssignedAt = LocalDateTime.now();
    }
}
