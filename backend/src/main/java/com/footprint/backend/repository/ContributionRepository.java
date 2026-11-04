package com.footprint.backend.repository;

import com.footprint.backend.entity.Contribution;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

// contribution 테이블을 저장·조회합니다.
public interface ContributionRepository extends JpaRepository<Contribution, Long> {

    // 한 조의 기여 항목을 최근에 등록한 것부터 찾습니다. id가 클수록 나중에 저장한 줄입니다.
    List<Contribution> findByTeamIdOrderByIdDesc(Long teamId);

    // 기여 항목을 찾으면서 그 행을 잠급니다. 다른 요청이 같은 항목을 잠그려면 이 트랜잭션이 끝날 때까지 기다립니다.
    // 메서드 이름의 "WithLock"은 읽기 쉽게 붙인 말이고, 실제 잠금은 @Lock이 합니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Contribution> findWithLockById(Long id);
}
