package com.footprint.backend.repository;

import com.footprint.backend.entity.LeaderHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// leader_history 테이블을 저장·조회합니다.
public interface LeaderHistoryRepository extends JpaRepository<LeaderHistory, Long> {

    // 한 조의 조장 교체 이력을 최근에 바뀐 것부터 찾습니다. id가 클수록 나중에 저장한 줄입니다.
    List<LeaderHistory> findByTeamIdOrderByIdDesc(Long teamId);
}
