package com.footprint.backend.repository;

import com.footprint.backend.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

// refresh_token 테이블을 저장·조회·삭제합니다.
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // hash로 행을 찾습니다.
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // hash가 같은 행을 지우고 지운 행의 수(0 또는 1)를 돌려줍니다.
    // @Modifying: SELECT가 아니라 DELETE를 실행하는 query라는 표시입니다. 돌려주는 int가 지운 행의 수입니다.
    // 같은 값으로 두 요청이 동시에 오면 PostgreSQL이 늦은 쪽의 DELETE를 먼저 온 쪽이 끝날 때까지 기다리게 합니다. 늦은 쪽은 0을 받습니다.
    @Modifying
    @Query("delete from RefreshToken r where r.tokenHash = :tokenHash")
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);

    // 한 회원의 만료된 행을 지웁니다. 로그인할 때 부릅니다.
    @Modifying
    @Query("delete from RefreshToken r where r.member.id = :memberId and r.expiresAt <= :now")
    int deleteExpiredByMemberId(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);
}
