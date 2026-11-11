package com.footprint.backend.service;

import com.footprint.backend.entity.Contribution;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.exception.DuplicateException;
import com.footprint.backend.exception.InvalidInputException;
import com.footprint.backend.exception.NotFoundException;
import com.footprint.backend.repository.ContributionRepository;
import com.footprint.backend.repository.TeamMemberRepository;
import com.footprint.backend.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 기여 항목을 등록하고, 목록을 찾고, 완료 보고를 받고, 취소합니다. 파일이 남지 않는 일의 기록입니다.
@Service
public class ContributionService {

    private final ContributionRepository contributionRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamAccessChecker teamAccessChecker;
    private final AssignmentPhaseChecker assignmentPhaseChecker;
    private final NotificationService notificationService;

    public ContributionService(ContributionRepository contributionRepository, TeamRepository teamRepository,
                               TeamMemberRepository teamMemberRepository, TeamAccessChecker teamAccessChecker,
                               AssignmentPhaseChecker assignmentPhaseChecker, NotificationService notificationService) {
        this.contributionRepository = contributionRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamAccessChecker = teamAccessChecker;
        this.assignmentPhaseChecker = assignmentPhaseChecker;
        this.notificationService = notificationService;
    }

    // 조장이 항목명과 담당 조원 한 명을 정해 기여 항목을 등록하고, 담당 조원에게 알림을 남깁니다. 조장 자신도 담당이 될 수 있습니다.
    @Transactional
    public Contribution create(Long teamId, String title, Long assigneeId, Long memberId) {
        Team team = findTeam(teamId);
        teamAccessChecker.checkLeader(team, memberId);
        assignmentPhaseChecker.checkRecording(team.getAssignment());

        // 다른 조의 조원은 담당이 될 수 없습니다. 조원 번호가 있어도 이 조의 조원이 아니면 없는 것으로 봅니다.
        TeamMember assignee = teamMemberRepository.findById(assigneeId)
                .filter(teamMember -> teamMember.getTeam().getId().equals(teamId))
                .orElseThrow(() -> new NotFoundException("이 조의 조원이 아닙니다. id=" + assigneeId));
        Contribution contribution = contributionRepository.save(new Contribution(team, assignee, title.trim()));
        // 같은 트랜잭션이라 알림 저장이 실패하면 등록도 취소됩니다.
        notificationService.notifyContributionAssigned(contribution, memberId);
        return contribution;
    }

    // 조의 기여 항목을 최근에 등록한 것부터 찾습니다. 취소된 항목도 빼지 않습니다. 그 조의 조원과 그 과제를 만든 교수가 볼 수 있습니다.
    public List<Contribution> findAll(Long teamId, Long memberId) {
        Team team = findTeam(teamId);
        teamAccessChecker.checkTeamAccess(team, memberId);
        return contributionRepository.findByTeamIdOrderByIdDesc(teamId);
    }

    // 담당 조원이 완료 보고하고, 같은 조의 다른 조원에게 알림을 남깁니다. 한 번만 할 수 있고, 메모는 보고한 뒤 고칠 수 없습니다.
    @Transactional
    public Contribution report(Long contributionId, String memo, Long memberId) {
        // 보고 요청 두 개가 겹치면 먼저 쓴 메모가 덮어써지고, 취소와 겹치면 둘 다 성공할 수 있습니다. 행을 잠가 한 번에 하나씩 처리합니다. 잠금은 트랜잭션이 끝나면 풀립니다.
        Contribution contribution = findContributionWithLock(contributionId);
        teamAccessChecker.checkAssignee(contribution, memberId);
        assignmentPhaseChecker.checkRecording(contribution.getTeam().getAssignment());
        if (contribution.isCanceled()) {
            throw new InvalidInputException("취소된 항목은 보고할 수 없습니다");
        }
        if (contribution.isReported()) {
            throw new DuplicateException("이미 완료 보고한 항목입니다");
        }

        contribution.report(memo.trim());
        notificationService.notifyContributionReported(contribution);
        return contribution;
    }

    // 조장이 보고 전 항목을 취소합니다. 행은 남고 목록에 "취소됨"으로 보입니다. 고치고 싶으면 취소하고 다시 등록합니다.
    @Transactional
    public Contribution cancel(Long contributionId, Long memberId) {
        // report와 같은 이유로 행을 잠급니다.
        Contribution contribution = findContributionWithLock(contributionId);
        teamAccessChecker.checkLeader(contribution.getTeam(), memberId);
        assignmentPhaseChecker.checkRecording(contribution.getTeam().getAssignment());
        if (contribution.isCanceled()) {
            throw new InvalidInputException("이미 취소된 항목입니다");
        }
        if (contribution.isReported()) {
            throw new InvalidInputException("보고한 항목은 취소할 수 없습니다");
        }

        contribution.cancel();
        return contribution;
    }

    // 기여 항목을 찾으면서 그 행을 잠급니다. 없으면 NotFoundException을 던집니다.
    private Contribution findContributionWithLock(Long contributionId) {
        return contributionRepository.findWithLockById(contributionId)
                .orElseThrow(() -> new NotFoundException("기여 항목이 없습니다. id=" + contributionId));
    }

    // 조를 찾습니다. 없으면 NotFoundException을 던집니다.
    private Team findTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("조가 없습니다. id=" + teamId));
    }
}
