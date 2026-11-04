package com.footprint.backend.repository;

import com.footprint.backend.entity.EvaluationDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// evaluation_draft 테이블을 저장·조회합니다. 본인 것만 찾습니다. 다른 사람의 임시 저장을 보는 조회는 만들지 않습니다.
public interface EvaluationDraftRepository extends JpaRepository<EvaluationDraft, Long> {

    // 한 평가자(조 참여 기록)의 임시 저장을 저장한 순서대로 찾습니다.
    List<EvaluationDraft> findByEvaluatorIdOrderByIdAsc(Long evaluatorId);
}
