package com.footprint.backend.repository;

import com.footprint.backend.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// team 테이블을 저장·조회합니다.
public interface TeamRepository extends JpaRepository<Team, Long> {

    // 초대 코드로 조를 찾습니다. 코드는 unique라서 0개 또는 1개입니다.
    Optional<Team> findByInviteCode(String inviteCode);
}
