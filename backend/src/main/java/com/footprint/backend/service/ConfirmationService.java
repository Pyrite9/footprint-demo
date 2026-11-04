package com.footprint.backend.service;

import com.footprint.backend.entity.Confirmation;
import com.footprint.backend.entity.Contribution;
import com.footprint.backend.entity.Deliverable;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.exception.DuplicateException;
import com.footprint.backend.exception.ForbiddenException;
import com.footprint.backend.exception.InvalidInputException;
import com.footprint.backend.exception.NotFoundException;
import com.footprint.backend.repository.ConfirmationRepository;
import com.footprint.backend.repository.ContributionRepository;
import com.footprint.backend.repository.DeliverableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 산출물과 보고된 기여 항목의 완료확인을 저장하고, 취소하고, 이력을 찾습니다. 건수를 세거나 점수로 바꾸지 않습니다.
@Service
public class ConfirmationService {

    private final ConfirmationRepository confirmationRepository;
    private final DeliverableRepository deliverableRepository;
    private final ContributionRepository contributionRepository;
    private final TeamAccessChecker teamAccessChecker;
    private final AssignmentPhaseChecker assignmentPhaseChecker;

    public ConfirmationService(ConfirmationRepository confirmationRepository, DeliverableRepository deliverableRepository,
                               ContributionRepository contributionRepository, TeamAccessChecker teamAccessChecker,
                               AssignmentPhaseChecker assignmentPhaseChecker) {
        this.confirmationRepository = confirmationRepository;
        this.deliverableRepository = deliverableRepository;
        this.contributionRepository = contributionRepository;
        this.teamAccessChecker = teamAccessChecker;
        this.assignmentPhaseChecker = assignmentPhaseChecker;
    }

    // 올린 조원을 뺀 같은 조 조원이 산출물을 확인합니다. 교수는 이력만 봅니다. 한 조원의 유효한 확인은 하나입니다.
    @Transactional
    public Confirmation confirmDeliverable(Long deliverableId, Long memberId) {
        // 같은 조원의 요청이 겹치면 둘 다 "아직 확인 안 함"으로 읽어 유효한 확인이 둘 생깁니다. 대상 행을 잠가 한 번에 하나씩 처리합니다. 잠금은 트랜잭션이 끝나면 풀립니다.
        Deliverable deliverable = deliverableRepository.findWithLockById(deliverableId)
                .orElseThrow(() -> new NotFoundException("산출물이 없습니다. id=" + deliverableId));
        Team team = deliverable.getTeamMember().getTeam();
        TeamMember confirmer = teamAccessChecker.checkTeamMember(team, memberId);
        if (deliverable.getTeamMember().getId().equals(confirmer.getId())) {
            throw new ForbiddenException("자기가 올린 산출물은 확인할 수 없습니다");
        }
        assignmentPhaseChecker.checkRecording(team.getAssignment());
        if (deliverable.isDeleted()) {
            throw new InvalidInputException("삭제된 산출물은 확인할 수 없습니다");
        }
        if (confirmationRepository.existsByDeliverableIdAndConfirmerIdAndCanceledAtIsNull(deliverableId, confirmer.getId())) {
            throw new DuplicateException("이미 확인한 기록입니다");
        }

        return confirmationRepository.save(new Confirmation(confirmer, deliverable));
    }

    // 담당 조원을 뺀 같은 조 조원이 보고된 기여 항목을 확인합니다. 규칙은 confirmDeliverable과 같습니다.
    @Transactional
    public Confirmation confirmContribution(Long contributionId, Long memberId) {
        // confirmDeliverable과 같은 이유로 대상 행을 잠급니다.
        Contribution contribution = contributionRepository.findWithLockById(contributionId)
                .orElseThrow(() -> new NotFoundException("기여 항목이 없습니다. id=" + contributionId));
        Team team = contribution.getTeam();
        TeamMember confirmer = teamAccessChecker.checkTeamMember(team, memberId);
        if (contribution.getAssignee().getId().equals(confirmer.getId())) {
            throw new ForbiddenException("자기가 맡은 항목은 확인할 수 없습니다");
        }
        assignmentPhaseChecker.checkRecording(team.getAssignment());
        if (contribution.isCanceled()) {
            throw new InvalidInputException("취소된 항목은 확인할 수 없습니다");
        }
        if (!contribution.isReported()) {
            throw new InvalidInputException("보고 전 항목은 확인할 수 없습니다");
        }
        if (confirmationRepository.existsByContributionIdAndConfirmerIdAndCanceledAtIsNull(contributionId, confirmer.getId())) {
            throw new DuplicateException("이미 확인한 기록입니다");
        }

        return confirmationRepository.save(new Confirmation(confirmer, contribution));
    }

    // 확인한 조원 본인이 자기 확인을 취소합니다. 행은 남고 이력에 "취소됨"으로 보입니다. 다시 확인하면 새 행이 생깁니다.
    // 삭제된 산출물의 확인도 취소할 수 있습니다. 내가 남긴 기록을 거두는 일이고 새로 확인하는 일이 아니기 때문입니다.
    @Transactional
    public Confirmation cancel(Long confirmationId, Long memberId) {
        // 취소 요청이 겹치면 뒤의 요청이 처음 취소 시각을 덮어씁니다. 행을 잠가 한 번에 하나씩 처리합니다.
        Confirmation confirmation = confirmationRepository.findWithLockById(confirmationId)
                .orElseThrow(() -> new NotFoundException("완료확인이 없습니다. id=" + confirmationId));
        teamAccessChecker.checkConfirmer(confirmation, memberId);
        assignmentPhaseChecker.checkRecording(confirmation.getConfirmer().getTeam().getAssignment());
        if (confirmation.isCanceled()) {
            throw new InvalidInputException("이미 취소한 확인입니다");
        }

        confirmation.cancel();
        return confirmation;
    }

    // 산출물의 완료확인 이력을 최근 것부터 찾습니다. 그 조의 조원과 그 과제를 만든 교수가 볼 수 있습니다. 삭제된 산출물의 이력도 남습니다.
    public List<Confirmation> findForDeliverable(Long deliverableId, Long memberId) {
        Deliverable deliverable = deliverableRepository.findById(deliverableId)
                .orElseThrow(() -> new NotFoundException("산출물이 없습니다. id=" + deliverableId));
        teamAccessChecker.checkTeamAccess(deliverable.getTeamMember().getTeam(), memberId);
        return confirmationRepository.findByDeliverableIdOrderByIdDesc(deliverableId);
    }

    // 기여 항목의 완료확인 이력을 최근 것부터 찾습니다. 그 조의 조원과 그 과제를 만든 교수가 볼 수 있습니다.
    public List<Confirmation> findForContribution(Long contributionId, Long memberId) {
        Contribution contribution = contributionRepository.findById(contributionId)
                .orElseThrow(() -> new NotFoundException("기여 항목이 없습니다. id=" + contributionId));
        teamAccessChecker.checkTeamAccess(contribution.getTeam(), memberId);
        return confirmationRepository.findByContributionIdOrderByIdDesc(contributionId);
    }
}
