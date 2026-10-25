package com.footprint.backend.repository;

import com.footprint.backend.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// assignment 테이블을 저장·조회합니다.
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    // 한 교수가 만든 과제를 최신순으로 찾습니다. "where professor_id = ? order by id desc"가 만들어집니다.
    // ProfessorId는 professor 필드의 id를 뜻합니다(professor.id).
    List<Assignment> findByProfessorIdOrderByIdDesc(Long professorId);
}
