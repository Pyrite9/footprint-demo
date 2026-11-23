package com.footprint.backend.repository;

import com.footprint.backend.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

// refresh_token 테이블을 저장·조회·고치기·삭제합니다.
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // hash로 행을 찾습니다.
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // hash가 같고 아직 쓰지 않은 행에 쓴 시각을 적고, 고친 행의 수(0 또는 1)를 돌려줍니다.
    // @Modifying: SELECT가 아니라 UPDATE를 실행하는 query라는 표시입니다. 돌려주는 int가 고친 행의 수입니다.
    // 같은 값으로 두 요청이 동시에 오면 PostgreSQL이 늦은 쪽의 UPDATE를 먼저 온 쪽이 끝날 때까지 기다리게 합니다.
    // 기다린 뒤에는 usedAt이 이미 채워져 있어서 조건에 맞지 않고, 늦은 쪽은 0을 받습니다.
    @Modifying
    @Query("update RefreshToken r set r.usedAt = :now where r.tokenHash = :tokenHash and r.usedAt is null")
    int markUsed(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    // 한 묶음(한 번의 로그인에서 이어진 refresh token들)의 행을 모두 지웁니다. 쓴 행도 함께 지웁니다.
    @Modifying
    @Query("delete from RefreshToken r where r.familyId = :familyId")
    int deleteByFamilyId(@Param("familyId") String familyId);

    // 모든 회원의 만료된 행을 지웁니다. 정해진 간격마다 서버가 부릅니다.
    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt <= :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
