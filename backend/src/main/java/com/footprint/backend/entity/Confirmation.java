package com.footprint.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// confirmation 테이블과 대응합니다. 조원이 산출물이나 보고된 기여 항목을 확인한 기록 하나가 행 하나입니다.
// 대상은 산출물(deliverable)과 기여 항목(contribution) 중 하나만 채웁니다. 대상 열을 둘로 나눠 외래 키 제약을 유지합니다.
// 취소해도 행을 지우지 않습니다. 취소 시각이 비어 있는 행이 유효한 확인입니다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Confirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 확인한 조원의 조 참여 기록입니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmer_id", nullable = false)
    private TeamMember confirmer;

    // 확인한 산출물입니다. 기여 항목을 확인한 행에서는 null입니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deliverable_id")
    private Deliverable deliverable;

    // 확인한 기여 항목입니다. 산출물을 확인한 행에서는 null입니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribution_id")
    private Contribution contribution;

    // 확인한 시각입니다. 서버가 기록하고, 사용자가 보낸 시각은 받지 않습니다.
    @Column(nullable = false)
    private LocalDateTime confirmedAt;

    // 취소 시각입니다. 취소하지 않았으면 null입니다.
    private LocalDateTime canceledAt;

    // 산출물을 확인한 기록을 만듭니다.
    public Confirmation(TeamMember confirmer, Deliverable deliverable) {
        this.confirmer = confirmer;
        this.deliverable = deliverable;
        this.confirmedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    // 기여 항목을 확인한 기록을 만듭니다.
    public Confirmation(TeamMember confirmer, Contribution contribution) {
        this.confirmer = confirmer;
        this.contribution = contribution;
        this.confirmedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
