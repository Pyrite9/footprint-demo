package com.footprint.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// team 테이블과 대응합니다. 과제 하나에 조가 여러 개 있습니다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이 조가 속한 과제입니다. LAZY인 이유는 Assignment.professor와 같습니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    // 화면에 보이는 조 이름입니다. 예: "1조"
    @Column(nullable = false)
    private String name;

    // 초대 링크에 들어가는 코드입니다. 조장이 발급하기(T-1-1) 전에는 비어 있습니다. 같은 코드가 두 조에 있을 수 없습니다.
    @Column(unique = true)
    private String inviteCode;

    public Team(Assignment assignment, String name) {
        this.assignment = assignment;
        this.name = name;
    }
}
