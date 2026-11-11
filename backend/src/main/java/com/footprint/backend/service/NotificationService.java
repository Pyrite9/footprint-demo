package com.footprint.backend.service;

import com.footprint.backend.entity.Assignment;
import com.footprint.backend.entity.Contribution;
import com.footprint.backend.entity.Member;
import com.footprint.backend.entity.Notification;
import com.footprint.backend.entity.NotificationType;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.exception.NotFoundException;
import com.footprint.backend.repository.NotificationRepository;
import com.footprint.backend.repository.TeamMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 화면 안 알림을 만들고, 찾고, 읽음으로 표시합니다.
// 알림은 평가 시작, 기여 항목 담당 지정, 완료 보고를 처리하는 Service가 아래 notify 메서드를 직접 불러서 만듭니다.
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamAccessChecker teamAccessChecker;

    public NotificationService(NotificationRepository notificationRepository, TeamMemberRepository teamMemberRepository,
                               TeamAccessChecker teamAccessChecker) {
        this.notificationRepository = notificationRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamAccessChecker = teamAccessChecker;
    }

    // 로그인한 회원이 받은 알림을 최근 것부터 찾습니다. 다른 회원의 알림은 들어가지 않습니다.
    public List<Notification> findMine(Long memberId) {
        return notificationRepository.findByRecipientIdOrderByIdDesc(memberId);
    }

    // 받은 회원 본인이 알림을 읽음으로 표시합니다. 이미 읽었으면 처음 읽은 시각을 그대로 둡니다.
    @Transactional
    public Notification markRead(Long notificationId, Long memberId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("알림이 없습니다. id=" + notificationId));
        teamAccessChecker.checkRecipient(notification, memberId);
        if (!notification.isRead()) {
            notification.markRead();
        }
        return notification;
    }

    // 평가가 시작된 과제의 모든 조원(조장 포함)에게 알림을 저장합니다. 과제를 만든 교수는 받지 않습니다.
    // @Transactional: AssignmentService.startEvaluation의 트랜잭션에 함께 들어갑니다. 저장이 실패하면 평가 시작도 취소됩니다.
    @Transactional
    public void notifyEvaluationStarted(Assignment assignment) {
        for (TeamMember teamMember : teamMemberRepository.findByTeamAssignmentIdOrderByIdAsc(assignment.getId())) {
            Team team = teamMember.getTeam();
            save(teamMember.getMember(), NotificationType.EVALUATION_STARTED,
                    prefixOf(team) + "평가가 시작되었습니다. 동료평가를 입력할 수 있습니다.", team);
        }
    }

    // 기여 항목의 담당 조원에게 알림을 저장합니다. 조장이 자신을 담당으로 정했으면 스스로 한 일이라 만들지 않습니다.
    @Transactional
    public void notifyContributionAssigned(Contribution contribution, Long leaderMemberId) {
        Member assignee = contribution.getAssignee().getMember();
        if (assignee.getId().equals(leaderMemberId)) {
            return;
        }
        Team team = contribution.getTeam();
        save(assignee, NotificationType.CONTRIBUTION_ASSIGNED,
                prefixOf(team) + "기여 항목 \"" + contribution.getTitle() + "\"의 담당이 되었습니다.", team);
    }

    // 완료 보고된 항목의 조에서 보고한 조원을 뺀 조원 전원(조장 포함)에게 알림을 저장합니다. 보고 메모는 담지 않습니다.
    // 조장만이 아니라 전원에게 보내는 이유: 담당 조원을 뺀 조원 누구나 완료확인을 할 수 있습니다.
    @Transactional
    public void notifyContributionReported(Contribution contribution) {
        Team team = contribution.getTeam();
        TeamMember reporter = contribution.getAssignee();
        String message = prefixOf(team) + reporter.getMember().getName() + " 님이 기여 항목 \""
                + contribution.getTitle() + "\" 완료 보고를 올렸습니다.";
        for (TeamMember teamMember : teamMemberRepository.findByTeamIdOrderByIdAsc(team.getId())) {
            if (teamMember.getId().equals(reporter.getId())) {
                continue;
            }
            save(teamMember.getMember(), NotificationType.CONTRIBUTION_REPORTED, message, team);
        }
    }

    // 알림 한 건을 저장합니다.
    private void save(Member recipient, NotificationType type, String message, Team team) {
        notificationRepository.save(new Notification(recipient, type, message, team));
    }

    // 알림 문장 앞에 붙일 "[과제 제목 조 이름] "입니다. 조 이름("1조")만으로는 어느 과제인지 알 수 없어서 과제 제목을 함께 씁니다.
    private String prefixOf(Team team) {
        return "[" + team.getAssignment().getTitle() + " " + team.getName() + "] ";
    }
}
