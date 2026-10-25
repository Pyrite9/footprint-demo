package com.footprint.backend.service;

import com.footprint.backend.entity.Assignment;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.exception.ForbiddenException;
import com.footprint.backend.repository.TeamMemberRepository;
import org.springframework.stereotype.Component;

// 조의 기록에 접근해도 되는지 확인합니다. 접근 범위 검사는 모두 이 클래스 한 곳에 모읍니다.
// 규칙: 그 조의 조원이거나, 그 과제를 만든 교수만 접근합니다. 아니면 ForbiddenException(403)을 던집니다.
// Service가 이 클래스의 메서드를 직접 부릅니다. 어노테이션으로 숨기지 않고 코드에 보이게 하기 위해서입니다.
@Component
public class TeamAccessChecker {

    private final TeamMemberRepository teamMemberRepository;

    public TeamAccessChecker(TeamMemberRepository teamMemberRepository) {
        this.teamMemberRepository = teamMemberRepository;
    }

    // 이 회원이 이 과제를 만든 교수이면 true입니다.
    public boolean isProfessorOf(Assignment assignment, Long memberId) {
        return assignment.getProfessor().getId().equals(memberId);
    }

    // 이 과제를 만든 교수이거나 이 과제의 조원인지 확인합니다. 둘 다 아니면 ForbiddenException을 던집니다.
    public void checkAssignmentAccess(Assignment assignment, Long memberId) {
        if (isProfessorOf(assignment, memberId)) {
            return;
        }
        checkAssignmentMember(assignment, memberId);
    }

    // 이 과제의 조원인지 확인하고 그 조 참여 기록을 돌려줍니다. 조원이 아니면 ForbiddenException을 던집니다.
    public TeamMember checkAssignmentMember(Assignment assignment, Long memberId) {
        return teamMemberRepository.findByTeamAssignmentIdAndMemberId(assignment.getId(), memberId)
                .orElseThrow(() -> new ForbiddenException("이 과제에 접근할 권한이 없습니다"));
    }

    // 이 조의 조원이거나 이 조가 속한 과제를 만든 교수인지 확인합니다. 둘 다 아니면 ForbiddenException을 던집니다.
    // 조의 기록(조원 목록, 산출물)을 볼 때 씁니다.
    public void checkTeamAccess(Team team, Long memberId) {
        if (isProfessorOf(team.getAssignment(), memberId)) {
            return;
        }
        checkTeamMember(team, memberId);
    }

    // 이 조의 조원인지 확인하고 그 조 참여 기록을 돌려줍니다. 조원이 아니면 ForbiddenException을 던집니다.
    // 조원만 할 수 있는 일(산출물 업로드, 동료평가)에 씁니다. 교수도 여기서는 막힙니다.
    public TeamMember checkTeamMember(Team team, Long memberId) {
        return teamMemberRepository.findByTeamIdAndMemberId(team.getId(), memberId)
                .orElseThrow(() -> new ForbiddenException("이 조에 접근할 권한이 없습니다"));
    }

    // 이 조의 조장인지 확인하고 그 조 참여 기록을 돌려줍니다. 조장이 아니면 ForbiddenException을 던집니다.
    // 조장만 할 수 있는 일(초대 코드 발급, 역할 지정)에 씁니다.
    public TeamMember checkLeader(Team team, Long memberId) {
        TeamMember teamMember = teamMemberRepository.findByTeamIdAndMemberId(team.getId(), memberId)
                .orElseThrow(() -> new ForbiddenException("조장만 할 수 있습니다"));
        if (!teamMember.isLeader()) {
            throw new ForbiddenException("조장만 할 수 있습니다");
        }
        return teamMember;
    }
}
