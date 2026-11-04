package com.footprint.backend.repository;

import com.footprint.backend.entity.Contribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// contribution 테이블을 저장·조회합니다.
public interface ContributionRepository extends JpaRepository<Contribution, Long> {

    // 한 조의 기여 항목을 최근에 등록한 것부터 찾습니다. id가 클수록 나중에 저장한 줄입니다.
    List<Contribution> findByTeamIdOrderByIdDesc(Long teamId);
}
