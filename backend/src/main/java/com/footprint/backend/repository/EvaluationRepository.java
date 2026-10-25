package com.footprint.backend.repository;

import com.footprint.backend.entity.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// evaluation 테이블을 저장·조회합니다. 조원끼리 서로의 평가를 보는 조회는 만들지 않습니다.
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    // 한 평가자(조 참여 기록)가 제출한 평가를 찾습니다. 제출 여부를 알 때만 씁니다.
    List<Evaluation> findByEvaluatorId(Long evaluatorId);
}
