package com.footprint.backend.service;

import com.footprint.backend.entity.Assignment;
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
}
