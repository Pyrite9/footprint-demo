package com.footprint.backend.repository;

import com.footprint.backend.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

// team 테이블을 저장·조회합니다.
public interface TeamRepository extends JpaRepository<Team, Long> {
}
