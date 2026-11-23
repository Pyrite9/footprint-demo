package com.footprint.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// refresh_token 테이블과 대응합니다. 행 하나가 refresh token 값 하나입니다.
// refresh token 값은 저장하지 않고 SHA-256 hash만 저장합니다. 데이터베이스가 새어도 값으로 로그인할 수 없게 하려는 것입니다.
// 재발급하면 쓴 행에 쓴 시각을 적고 새 행을 같은 묶음으로 만듭니다(교체). 쓴 행은 재사용을 알아보려고 남겨 둡니다.
// 쓴 시각은 RefreshTokenRepository.markUsed(update query)로 적어서 Entity에 값을 고치는 메서드가 없습니다.
// update query로 적는 이유: "아직 안 쓴 행만 고친다"를 한 문장으로 해야 같은 값으로 동시에 온 두 요청 중 하나만 성공합니다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이 token으로 로그인하는 회원입니다. 한 회원이 브라우저마다 하나씩 여러 행을 가질 수 있습니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // refresh token 값의 SHA-256 hash를 16진수 64글자로 바꾼 것입니다. 같은 hash는 두 번 저장할 수 없습니다.
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    // 묶음(family) id입니다. 한 번의 로그인에서 재발급으로 이어진 refresh token들은 같은 묶음 id를 갖습니다.
    // 로그인할 때 UUID 36글자로 새로 만들고, 재발급한 행은 쓴 행의 것을 물려받습니다. 재사용을 감지하거나 로그아웃하면 묶음째 지웁니다.
    @Column(nullable = false, length = 36)
    private String familyId;

    // 이 시각이 지나면 쓸 수 없습니다. 로그인한 시각부터 세고, 재발급해도 늘어나지 않습니다(절대 만료).
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    // 이 행을 만든 시각입니다. 서버가 기록합니다.
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 재발급에 쓴 시각입니다. 아직 쓰지 않았으면 null입니다. 서버가 기록합니다.
    private LocalDateTime usedAt;

    // 새 refresh token 행을 만듭니다. 쓴 시각은 비어 있습니다.
    public RefreshToken(Member member, String tokenHash, String familyId, LocalDateTime expiresAt) {
        this.member = member;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    // 만료 시각이 지났으면 true입니다.
    public boolean isExpired() {
        return !LocalDateTime.now().isBefore(expiresAt);
    }

    // 이미 재발급에 쓴 값이면 true입니다.
    public boolean isUsed() {
        return usedAt != null;
    }
}
