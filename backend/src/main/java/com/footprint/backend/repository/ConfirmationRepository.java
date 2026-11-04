package com.footprint.backend.repository;

import com.footprint.backend.entity.Confirmation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// confirmation 테이블을 저장·조회합니다.
public interface ConfirmationRepository extends JpaRepository<Confirmation, Long> {

    // 한 산출물의 완료확인을 최근 것부터 찾습니다. 취소된 확인도 빼지 않습니다.
    List<Confirmation> findByDeliverableIdOrderByIdDesc(Long deliverableId);

    // 한 기여 항목의 완료확인을 최근 것부터 찾습니다. 취소된 확인도 빼지 않습니다.
    List<Confirmation> findByContributionIdOrderByIdDesc(Long contributionId);

    // 이 조원의 유효한(취소하지 않은) 확인이 이 산출물에 있으면 true입니다.
    boolean existsByDeliverableIdAndConfirmerIdAndCanceledAtIsNull(Long deliverableId, Long confirmerId);

    // 이 조원의 유효한(취소하지 않은) 확인이 이 기여 항목에 있으면 true입니다.
    boolean existsByContributionIdAndConfirmerIdAndCanceledAtIsNull(Long contributionId, Long confirmerId);
}
