package com.footprint.backend.repository;

import com.footprint.backend.entity.Confirmation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

// confirmation 테이블을 저장·조회합니다.
public interface ConfirmationRepository extends JpaRepository<Confirmation, Long> {

    // 한 산출물의 완료확인을 최근 것부터 찾습니다. 취소된 확인도 빼지 않습니다.
    List<Confirmation> findByDeliverableIdOrderByIdDesc(Long deliverableId);

    // 한 기여 항목의 완료확인을 최근 것부터 찾습니다. 취소된 확인도 빼지 않습니다.
    List<Confirmation> findByContributionIdOrderByIdDesc(Long contributionId);

    // 한 조원이 올린 산출물에 남은 완료확인을 최근 것부터 찾습니다. DeliverableTeamMemberId는 deliverable.teamMember.id입니다.
    List<Confirmation> findByDeliverableTeamMemberIdOrderByIdDesc(Long teamMemberId);

    // 한 조원이 맡은 기여 항목에 남은 완료확인을 최근 것부터 찾습니다. ContributionAssigneeId는 contribution.assignee.id입니다.
    List<Confirmation> findByContributionAssigneeIdOrderByIdDesc(Long teamMemberId);

    // 한 조원이 남긴 완료확인을 최근 것부터 찾습니다.
    List<Confirmation> findByConfirmerIdOrderByIdDesc(Long confirmerId);

    // 한 조의 조원이 남긴 완료확인을 최근 것부터 찾습니다. ConfirmerTeamId는 confirmer.team.id입니다.
    List<Confirmation> findByConfirmerTeamIdOrderByIdDesc(Long teamId);

    // 이 조원의 유효한(취소하지 않은) 확인이 이 산출물에 있으면 true입니다.
    boolean existsByDeliverableIdAndConfirmerIdAndCanceledAtIsNull(Long deliverableId, Long confirmerId);

    // 이 조원의 유효한(취소하지 않은) 확인이 이 기여 항목에 있으면 true입니다.
    boolean existsByContributionIdAndConfirmerIdAndCanceledAtIsNull(Long contributionId, Long confirmerId);

    // 완료확인을 찾으면서 그 행을 잠급니다. 취소 요청이 겹쳐도 처음 취소 시각이 덮어써지지 않게 합니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Confirmation> findWithLockById(Long id);
}
