package com.footprint.backend.repository;

import com.footprint.backend.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;

// team_member 테이블을 저장·조회합니다.
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
}
