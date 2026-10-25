package com.footprint.backend.repository;

import com.footprint.backend.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

// assignment 테이블을 저장·조회합니다.
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
}
