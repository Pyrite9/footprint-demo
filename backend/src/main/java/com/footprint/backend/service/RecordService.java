package com.footprint.backend.service;

import com.footprint.backend.entity.Team;
import com.footprint.backend.exception.NotFoundException;
import com.footprint.backend.repository.ConfirmationRepository;
import com.footprint.backend.repository.ContributionRepository;
import com.footprint.backend.repository.DeliverableRepository;
import com.footprint.backend.repository.LeaderHistoryRepository;
import com.footprint.backend.repository.RoleHistoryRepository;
import com.footprint.backend.repository.TeamMemberRepository;
import com.footprint.backend.repository.TeamRepository;
import org.springframework.stereotype.Service;

// 기록 열람에 보여 줄 한 조의 기록을 모읍니다. 동료평가와 임시 저장은 모으지 않습니다. 건수를 세거나 점수로 바꾸지 않습니다.
@Service
public class RecordService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final RoleHistoryRepository roleHistoryRepository;
    private final LeaderHistoryRepository leaderHistoryRepository;
    private final DeliverableRepository deliverableRepository;
    private final ContributionRepository contributionRepository;
    private final ConfirmationRepository confirmationRepository;
    private final TeamAccessChecker teamAccessChecker;

    public RecordService(TeamRepository teamRepository, TeamMemberRepository teamMemberRepository,
                         RoleHistoryRepository roleHistoryRepository, LeaderHistoryRepository leaderHistoryRepository,
                         DeliverableRepository deliverableRepository, ContributionRepository contributionRepository,
                         ConfirmationRepository confirmationRepository, TeamAccessChecker teamAccessChecker) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.roleHistoryRepository = roleHistoryRepository;
        this.leaderHistoryRepository = leaderHistoryRepository;
        this.deliverableRepository = deliverableRepository;
        this.contributionRepository = contributionRepository;
        this.confirmationRepository = confirmationRepository;
        this.teamAccessChecker = teamAccessChecker;
    }

    // 한 조의 기록을 찾습니다. 그 조의 조원과 그 과제를 만든 교수가 기록 단계와 평가 단계 모두 볼 수 있습니다.
    public TeamRecords find(Long teamId, Long memberId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("조가 없습니다. id=" + teamId));
        teamAccessChecker.checkTeamAccess(team, memberId);

        return new TeamRecords(
                team.getAssignment().getEvaluationStartedAt(),
                leaderHistoryRepository.findByTeamIdOrderByIdDesc(teamId),
                confirmationRepository.findByConfirmerTeamIdOrderByIdDesc(teamId),
                contributionRepository.findByTeamIdOrderByIdDesc(teamId),
                deliverableRepository.findByTeamMemberTeamIdOrderByIdDesc(teamId),
                teamMemberRepository.findByTeamIdOrderByIdAsc(teamId),
                roleHistoryRepository.findByTeamMemberTeamIdOrderByIdDesc(teamId));
    }
}
