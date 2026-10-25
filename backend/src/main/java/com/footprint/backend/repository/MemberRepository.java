package com.footprint.backend.repository;

import com.footprint.backend.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// member 테이블을 저장·조회합니다.
public interface MemberRepository extends JpaRepository<Member, Long> {

    // 메서드 이름(findBy + 필드 이름)만 보고 Spring Data JPA가 "where email = ?" 조회를 만들어 줍니다. 없으면 빈 Optional입니다.
    Optional<Member> findByEmail(String email);
}
