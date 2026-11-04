package com.footprint.backend.repository;

import com.footprint.backend.entity.Deliverable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

// deliverable 테이블을 저장·조회합니다.
public interface DeliverableRepository extends JpaRepository<Deliverable, Long> {

    // 한 조의 산출물을 최신순(id 큰 순)으로 찾습니다. TeamMemberTeamId는 teamMember.team.id입니다.
    List<Deliverable> findByTeamMemberTeamIdOrderByIdDesc(Long teamId);

    // 산출물을 찾으면서 그 행을 잠급니다. 완료확인 요청이 겹쳐도 한 번에 하나씩 처리하려고 씁니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Deliverable> findWithLockById(Long id);
}
