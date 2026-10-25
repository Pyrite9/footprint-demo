package com.footprint.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// assignment 테이블과 대응합니다. 교수가 만든 과제 하나가 행 하나입니다. 등록한 과제는 고치거나 지우지 않습니다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    // 과제를 만든 교수입니다. 테이블에는 professor_id 열로 저장됩니다.
    // LAZY: 과제를 읽을 때 교수 정보는 실제로 꺼내 쓸 때까지 조회하지 않습니다. 기본값(EAGER)이면 목록마다 쿼리가 줄줄이 붙습니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "professor_id", nullable = false)
    private Member professor;

    public Assignment(String title, LocalDate startDate, LocalDate endDate, Member professor) {
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.professor = professor;
    }
}
