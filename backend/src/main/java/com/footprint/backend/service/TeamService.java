package com.footprint.backend.service;

import com.footprint.backend.entity.Member;
import com.footprint.backend.entity.MemberType;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.exception.DuplicateException;
import com.footprint.backend.exception.ForbiddenException;
import com.footprint.backend.exception.NotFoundException;
import com.footprint.backend.repository.TeamMemberRepository;
import com.footprint.backend.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 조 정보와 조원 목록을 조회하고, 초대 코드 발급, 초대 코드로 합류, 역할 지정을 처리합니다.
@Service
public class TeamService {

    private final MemberService memberService;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamAccessChecker teamAccessChecker;

    public TeamService(MemberService memberService, TeamRepository teamRepository,
                       TeamMemberRepository teamMemberRepository, TeamAccessChecker teamAccessChecker) {
        this.memberService = memberService;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamAccessChecker = teamAccessChecker;
    }

    // 조 하나를 찾습니다. 그 조의 조원이나 그 과제를 만든 교수가 아니면 ForbiddenException을 던집니다.
    public Team findById(Long teamId, Long memberId) {
        Team team = findTeam(teamId);
        teamAccessChecker.checkTeamAccess(team, memberId);
        return team;
    }

    // 조원 목록을 합류한 순서로 찾습니다. 권한은 findById와 같습니다.
    public List<TeamMember> findMembers(Long teamId, Long memberId) {
        Team team = findById(teamId, memberId);
        return teamMemberRepository.findByTeamIdOrderByIdAsc(team.getId());
    }

    // 지금 유효한 초대 코드를 찾습니다. 조장만 볼 수 있습니다. 발급한 적이 없으면 null입니다.
    public String findInviteCode(Long teamId, Long memberId) {
        Team team = findTeam(teamId);
        teamAccessChecker.checkLeader(team, memberId);
        return team.getInviteCode();
    }

    // 초대 코드를 새로 발급합니다. 조장만 할 수 있습니다. 이전 코드는 무효가 됩니다.
    // @Transactional: 메서드가 끝날 때 바뀐 inviteCode가 DB에 저장됩니다(save를 따로 부르지 않아도 됩니다).
    @Transactional
    public String issueInviteCode(Long teamId, Long memberId) {
        Team team = findTeam(teamId);
        teamAccessChecker.checkLeader(team, memberId);
        // UUID는 겹칠 일이 사실상 없는 무작위 문자열입니다. 짐작해서 다른 조에 들어갈 수 없게 합니다.
        team.changeInviteCode(UUID.randomUUID().toString());
        return team.getInviteCode();
    }

    // 초대 코드로 그 조에 합류합니다. 이미 그 조의 조원이면 아무것도 바꾸지 않고 그 조를 돌려줍니다.
    @Transactional
    public Team join(String inviteCode, Long memberId) {
        Team team = teamRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new NotFoundException("초대 코드가 올바르지 않습니다"));
        Member member = memberService.findById(memberId);
        if (member.getMemberType() != MemberType.STUDENT) {
            throw new ForbiddenException("학생만 조에 합류할 수 있습니다");
        }

        // 한 학생은 같은 과제에서 한 조에만 있습니다. 같은 과제의 조 참여 기록이 있는지 먼저 봅니다.
        Optional<TeamMember> existing = teamMemberRepository.findByTeamAssignmentIdAndMemberId(team.getAssignment().getId(), memberId);
        if (existing.isPresent()) {
            if (existing.get().getTeam().getId().equals(team.getId())) {
                return team;
            }
            throw new DuplicateException("이미 이 과제의 다른 조에 있습니다");
        }

        teamMemberRepository.save(new TeamMember(team, member, false));
        return team;
    }

    // 조장이 조원 한 명에게 역할을 지정합니다. 역할이 없는 조원에게만 지정할 수 있습니다(역할 변경은 2단계).
    @Transactional
    public TeamMember assignRole(Long teamId, Long teamMemberId, String roleName, Long memberId) {
        Team team = findTeam(teamId);
        teamAccessChecker.checkLeader(team, memberId);

        // 다른 조의 조원 번호를 넣어도 찾지 못하도록 조 번호와 함께 확인합니다.
        TeamMember target = teamMemberRepository.findById(teamMemberId)
                .filter(teamMember -> teamMember.getTeam().getId().equals(teamId))
                .orElseThrow(() -> new NotFoundException("이 조의 조원이 아닙니다. id=" + teamMemberId));
        if (target.getRoleName() != null) {
            throw new DuplicateException("이미 역할이 지정된 조원입니다");
        }

        // 앞뒤 공백을 지웁니다. " 백엔드"와 "백엔드"가 다른 역할로 나뉘면 산출물 순번이 끊깁니다.
        target.assignRole(roleName.trim());
        return target;
    }

    // 조를 찾습니다. 없으면 NotFoundException을 던집니다.
    private Team findTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("조가 없습니다. id=" + teamId));
    }
}
