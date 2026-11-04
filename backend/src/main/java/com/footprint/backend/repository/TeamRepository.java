package com.footprint.backend.repository;

import com.footprint.backend.entity.Team;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

// team 테이블을 저장·조회합니다.
public interface TeamRepository extends JpaRepository<Team, Long> {

    // 초대 코드로 조를 찾습니다. 코드는 unique라서 0개 또는 1개입니다.
    Optional<Team> findByInviteCode(String inviteCode);

    // 조를 찾으면서 그 행을 잠급니다. 다른 요청이 같은 조를 잠그려면 이 트랜잭션이 끝날 때까지 기다립니다.
    // 메서드 이름의 "WithLock"은 읽기 쉽게 붙인 말이고, 실제 잠금은 @Lock이 합니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Team> findWithLockById(Long id);
}
