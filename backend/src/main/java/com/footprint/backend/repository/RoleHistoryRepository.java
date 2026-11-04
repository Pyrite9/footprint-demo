package com.footprint.backend.repository;

import com.footprint.backend.entity.RoleHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// role_history 테이블을 저장·조회합니다.
public interface RoleHistoryRepository extends JpaRepository<RoleHistory, Long> {

    // 한 조원의 역할 이력을 최근에 바뀐 것부터 찾습니다. id가 클수록 나중에 저장한 줄입니다.
    List<RoleHistory> findByTeamMemberIdOrderByIdDesc(Long teamMemberId);
}
