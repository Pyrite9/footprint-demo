package com.footprint.backend.service;

import com.footprint.backend.entity.Confirmation;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.exception.NotFoundException;
import com.footprint.backend.repository.ConfirmationRepository;
import com.footprint.backend.repository.ContributionRepository;
import com.footprint.backend.repository.DeliverableRepository;
import com.footprint.backend.repository.TeamMemberRepository;
import com.footprint.backend.repository.TeamRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 근거 패널에 보여 줄 한 조원의 기록을 종류별로 모읍니다. 건수를 세거나 점수로 바꾸지 않습니다.
@Service
public class EvidenceService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final DeliverableRepository deliverableRepository;
    private final ContributionRepository contributionRepository;
    private final ConfirmationRepository confirmationRepository;
    private final TeamAccessChecker teamAccessChecker;
    private final AssignmentPhaseChecker assignmentPhaseChecker;

    public EvidenceService(TeamRepository teamRepository, TeamMemberRepository teamMemberRepository,
                           DeliverableRepository deliverableRepository, ContributionRepository contributionRepository,
                           ConfirmationRepository confirmationRepository, TeamAccessChecker teamAccessChecker,
                           AssignmentPhaseChecker assignmentPhaseChecker) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.deliverableRepository = deliverableRepository;
        this.contributionRepository = contributionRepository;
        this.confirmationRepository = confirmationRepository;
        this.teamAccessChecker = teamAccessChecker;
        this.assignmentPhaseChecker = assignmentPhaseChecker;
    }

    // 한 조원의 기록을 찾습니다. 과제를 만든 교수는 단계와 상관없이, 같은 조 조원은 평가 단계에서만 봅니다.
    public TeamMemberEvidence find(Long teamId, Long teamMemberId, Long memberId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("조가 없습니다. id=" + teamId));
        if (!teamAccessChecker.isProfessorOf(team.getAssignment(), memberId)) {
            teamAccessChecker.checkTeamMember(team, memberId);
            assignmentPhaseChecker.checkEvidenceOpen(team.getAssignment());
        }
        TeamMember target = teamMemberRepository.findById(teamMemberId)
                .filter(teamMember -> teamMember.getTeam().getId().equals(teamId))
                .orElseThrow(() -> new NotFoundException("이 조의 조원이 아닙니다. id=" + teamMemberId));

        // 산출물의 확인과 기여 항목의 확인을 따로 찾아 합친 뒤 최근 것부터 놓습니다. id가 클수록 나중에 저장한 줄입니다.
        List<Confirmation> receivedConfirmations = new ArrayList<>(confirmationRepository.findByDeliverableTeamMemberIdOrderByIdDesc(target.getId()));
        receivedConfirmations.addAll(confirmationRepository.findByContributionAssigneeIdOrderByIdDesc(target.getId()));
        receivedConfirmations.sort(Comparator.comparing(Confirmation::getId).reversed());

        return new TeamMemberEvidence(
                deliverableRepository.findByTeamMemberIdOrderByIdDesc(target.getId()),
                contributionRepository.findByAssigneeIdOrderByIdDesc(target.getId()),
                receivedConfirmations,
                confirmationRepository.findByConfirmerIdOrderByIdDesc(target.getId()));
    }
}
