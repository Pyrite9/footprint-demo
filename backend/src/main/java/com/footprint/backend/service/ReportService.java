package com.footprint.backend.service;

import com.footprint.backend.entity.Report;
import com.footprint.backend.entity.ReportPart;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.exception.InvalidInputException;
import com.footprint.backend.exception.NotFoundException;
import com.footprint.backend.repository.ReportPartRepository;
import com.footprint.backend.repository.ReportRepository;
import com.footprint.backend.repository.TeamMemberRepository;
import com.footprint.backend.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 보고서를 만들고, 파트를 나눠 담당을 정하고, 파트를 취소합니다. 파트의 글은 X-12-2에서 더합니다.
@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final ReportPartRepository reportPartRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamAccessChecker teamAccessChecker;
    private final AssignmentPhaseChecker assignmentPhaseChecker;
    private final NotificationService notificationService;

    public ReportService(ReportRepository reportRepository, ReportPartRepository reportPartRepository,
                         TeamRepository teamRepository, TeamMemberRepository teamMemberRepository,
                         TeamAccessChecker teamAccessChecker, AssignmentPhaseChecker assignmentPhaseChecker,
                         NotificationService notificationService) {
        this.reportRepository = reportRepository;
        this.reportPartRepository = reportPartRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamAccessChecker = teamAccessChecker;
        this.assignmentPhaseChecker = assignmentPhaseChecker;
        this.notificationService = notificationService;
    }

    // 조장이 제목으로 보고서를 만듭니다. 한 조에 여러 개를 만들 수 있습니다.
    @Transactional
    public Report create(Long teamId, String title, Long memberId) {
        Team team = findTeam(teamId);
        teamAccessChecker.checkLeader(team, memberId);
        assignmentPhaseChecker.checkRecording(team.getAssignment());
        return reportRepository.save(new Report(team, title.trim()));
    }

    // 조의 보고서를 최근에 만든 것부터 찾습니다. 그 조의 조원과 그 과제를 만든 교수가 볼 수 있습니다.
    public List<Report> findAll(Long teamId, Long memberId) {
        Team team = findTeam(teamId);
        teamAccessChecker.checkTeamAccess(team, memberId);
        return reportRepository.findByTeamIdOrderByIdDesc(teamId);
    }

    // 조장이 파트 제목과 담당 조원 한 명을 정해 파트를 등록하고, 담당 조원에게 알림을 남깁니다. 조장 자신도 담당이 될 수 있습니다.
    @Transactional
    public ReportPart createPart(Long reportId, String title, Long assigneeId, Long memberId) {
        Report report = findReport(reportId);
        Team team = report.getTeam();
        teamAccessChecker.checkLeader(team, memberId);
        assignmentPhaseChecker.checkRecording(team.getAssignment());

        // 다른 조의 조원은 담당이 될 수 없습니다. 조원 번호가 있어도 이 조의 조원이 아니면 없는 것으로 봅니다.
        TeamMember assignee = teamMemberRepository.findById(assigneeId)
                .filter(teamMember -> teamMember.getTeam().getId().equals(team.getId()))
                .orElseThrow(() -> new NotFoundException("이 조의 조원이 아닙니다. id=" + assigneeId));
        ReportPart part = reportPartRepository.save(new ReportPart(report, assignee, title.trim()));
        // 같은 트랜잭션이라 알림 저장이 실패하면 등록도 취소됩니다.
        notificationService.notifyReportPartAssigned(part, memberId);
        return part;
    }

    // 보고서의 파트를 먼저 등록한 것부터 찾습니다. 취소된 파트도 빼지 않습니다. 그 조의 조원과 그 과제를 만든 교수가 볼 수 있습니다.
    public List<ReportPart> findParts(Long reportId, Long memberId) {
        Report report = findReport(reportId);
        teamAccessChecker.checkTeamAccess(report.getTeam(), memberId);
        return reportPartRepository.findByReportIdOrderByIdAsc(reportId);
    }

    // 조장이 파트를 취소합니다. 행은 남고 목록에 "취소됨"으로 보이며 합본에서 빠집니다. 고치고 싶으면 취소하고 다시 등록합니다.
    @Transactional
    public ReportPart cancelPart(Long partId, Long memberId) {
        // 취소 요청 두 개가 겹치면 둘 다 성공할 수 있습니다. 행을 잠가 한 번에 하나씩 처리합니다. 잠금은 트랜잭션이 끝나면 풀립니다.
        ReportPart part = reportPartRepository.findWithLockById(partId)
                .orElseThrow(() -> new NotFoundException("보고서 파트가 없습니다. id=" + partId));
        Team team = part.getReport().getTeam();
        teamAccessChecker.checkLeader(team, memberId);
        assignmentPhaseChecker.checkRecording(team.getAssignment());
        if (part.isCanceled()) {
            throw new InvalidInputException("이미 취소된 파트입니다");
        }

        part.cancel();
        return part;
    }

    // 보고서를 찾습니다. 없으면 NotFoundException을 던집니다.
    private Report findReport(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("보고서가 없습니다. id=" + reportId));
    }

    // 조를 찾습니다. 없으면 NotFoundException을 던집니다.
    private Team findTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("조가 없습니다. id=" + teamId));
    }
}
