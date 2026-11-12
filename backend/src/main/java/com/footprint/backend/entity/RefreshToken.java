package com.footprint.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// refresh_token 테이블과 대응합니다. 로그인한 브라우저 하나에 refresh token 하나입니다.
// refresh token 값은 저장하지 않고 SHA-256 hash만 저장합니다. 데이터베이스가 새어도 값으로 로그인할 수 없게 하려는 것입니다.
// 재발급할 때마다 행을 지우고 새로 만들어서(교체) 고치는 메서드가 없습니다.
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

    // 이 시각이 지나면 쓸 수 없습니다. 로그인한 시각부터 세고, 재발급해도 늘어나지 않습니다(절대 만료).
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    // 이 행을 만든 시각입니다. 서버가 기록합니다.
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public RefreshToken(Member member, String tokenHash, LocalDateTime expiresAt) {
        this.member = member;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    // 만료 시각이 지났으면 true입니다.
    public boolean isExpired() {
        return !LocalDateTime.now().isBefore(expiresAt);
    }
}
