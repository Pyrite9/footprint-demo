package com.footprint.backend.repository;

import com.footprint.backend.entity.Deliverable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// deliverable 테이블을 저장·조회합니다.
public interface DeliverableRepository extends JpaRepository<Deliverable, Long> {

    // 한 조의 산출물을 최신순(id 큰 순)으로 찾습니다. TeamMemberTeamId는 teamMember.team.id입니다.
    List<Deliverable> findByTeamMemberTeamIdOrderByIdDesc(Long teamId);
}
