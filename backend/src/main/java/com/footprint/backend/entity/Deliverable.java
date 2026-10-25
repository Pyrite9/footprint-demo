package com.footprint.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// deliverable 테이블과 대응합니다. 조원이 올린 산출물 파일 하나가 행 하나입니다. 고친 파일도 새 행으로 올립니다.
// 파일 본문은 MinIO에 있고, 여기에는 저장 위치(storageKey), 파일 이름, 크기만 둡니다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deliverable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 올린 조원의 조 참여 기록입니다. 회원이 아니라 조 참여를 참조합니다(조마다 역할이 다르고, 조 단위 권한 검사의 기준이기 때문입니다).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_member_id", nullable = false)
    private TeamMember teamMember;

    // 올릴 때의 역할명을 복사해 둔 값입니다. 나중에 역할이 바뀌어도 이 산출물의 역할은 그대로라서 순번이 끊기지 않습니다.
    @Column(nullable = false)
    private String roleName;

    // 올린 파일의 원래 이름입니다. 다운로드할 때 이 이름으로 내려줍니다.
    @Column(nullable = false)
    private String fileName;

    // 파일 크기(byte)입니다.
    @Column(nullable = false)
    private long fileSize;

    // MinIO에서 파일 본문을 꺼낼 때 쓰는 저장 위치(key)입니다.
    @Column(nullable = false)
    private String storageKey;

    // 올린 시각입니다. 서버가 기록하고, 사용자가 보낸 시각은 받지 않습니다.
    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    public Deliverable(TeamMember teamMember, String roleName, String fileName, long fileSize, String storageKey) {
        this.teamMember = teamMember;
        this.roleName = roleName;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.storageKey = storageKey;
        // 초 아래(나노초)는 버립니다. 화면에 "2026-11-05T14:03:22"처럼 보이게 하기 위해서입니다.
        this.uploadedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
