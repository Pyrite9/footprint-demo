package com.footprint.backend.service;

import com.footprint.backend.entity.LeaderHistory;
import com.footprint.backend.entity.Member;
import com.footprint.backend.entity.MemberType;
import com.footprint.backend.entity.RoleHistory;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.exception.DuplicateException;
import com.footprint.backend.exception.ForbiddenException;
import com.footprint.backend.exception.InvalidInputException;
import com.footprint.backend.exception.NotFoundException;
import com.footprint.backend.repository.LeaderHistoryRepository;
import com.footprint.backend.repository.RoleHistoryRepository;
import com.footprint.backend.repository.TeamMemberRepository;
import com.footprint.backend.repository.TeamRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 조 정보와 조원 목록을 조회하고, 초대 코드 발급, 초대 코드로 합류, 역할 지정·변경, 역할 이력 조회, 조장 교체를 처리합니다.
@Service
public class TeamService {

    private final MemberService memberService;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final RoleHistoryRepository roleHistoryRepository;
    private final LeaderHistoryRepository leaderHistoryRepository;
    private final TeamAccessChecker teamAccessChecker;
    private final AssignmentPhaseChecker assignmentPhaseChecker;

    public TeamService(MemberService memberService, TeamRepository teamRepository,
                       TeamMemberRepository teamMemberRepository, RoleHistoryRepository roleHistoryRepository,
                       LeaderHistoryRepository leaderHistoryRepository, TeamAccessChecker teamAccessChecker,
                       AssignmentPhaseChecker assignmentPhaseChecker) {
        this.memberService = memberService;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.roleHistoryRepository = roleHistoryRepository;
        this.leaderHistoryRepository = leaderHistoryRepository;
        this.teamAccessChecker = teamAccessChecker;
        this.assignmentPhaseChecker = assignmentPhaseChecker;
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

    // 초대 코드를 보려는 조를 찾습니다. 조장만 볼 수 있습니다. 발급한 적이 없으면 조의 inviteCode가 null입니다.
    public Team findInviteCode(Long teamId, Long memberId) {
        Team team = findTeam(teamId);
        teamAccessChecker.checkLeader(team, memberId);
        return team;
    }

    // 초대 코드를 새로 발급하고 그 조를 돌려줍니다. 조장만 할 수 있습니다. 이전 코드는 무효가 됩니다.
    // @Transactional: 메서드가 끝날 때 바뀐 inviteCode가 DB에 저장됩니다(save를 따로 부르지 않아도 됩니다).
    @Transactional
    public Team issueInviteCode(Long teamId, Long memberId) {
        Team team = findTeam(teamId);
        teamAccessChecker.checkLeader(team, memberId);
        assignmentPhaseChecker.checkRecording(team.getAssignment());
        // UUID는 겹칠 일이 사실상 없는 무작위 문자열입니다. 짐작해서 다른 조에 들어갈 수 없게 합니다.
        team.changeInviteCode(UUID.randomUUID().toString());
        return team;
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
        // 위에서 이미 조원인 경우를 돌려보낸 뒤에 검사합니다. 조원이 다시 눌러도 아무것도 바뀌지 않아 막을 이유가 없습니다.
        assignmentPhaseChecker.checkRecording(team.getAssignment());

        // 위 검사를 두 요청(같은 학생, 같은 과제의 두 조)이 거의 동시에 통과하면, 두 번째 요청은 DB의 unique 제약에 걸립니다.
        // saveAndFlush는 INSERT를 지금 바로 보내서 그 오류를 여기서 잡아 409로 바꿀 수 있게 합니다.
        // 이때는 같은 조에 두 번 누른 것인지 다른 조인지 다시 조회할 수 없어서(트랜잭션이 이미 실패함) 문구를 넓게 씁니다.
        try {
            teamMemberRepository.saveAndFlush(new TeamMember(team, member, false));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateException("이미 이 과제의 조에 있습니다");
        }
        return team;
    }

    // 조장이 조원 한 명에게 역할을 지정합니다. 역할이 없는 조원에게만 지정할 수 있습니다. 이미 역할이 있으면 changeRole을 씁니다.
    @Transactional
    public TeamMember assignRole(Long teamId, Long teamMemberId, String roleName, Long memberId) {
        Team team = findTeam(teamId);
        teamAccessChecker.checkLeader(team, memberId);
        assignmentPhaseChecker.checkRecording(team.getAssignment());

        // 다른 조의 조원 번호를 넣어도 찾지 못하도록 조 번호와 함께 확인합니다.
        TeamMember target = findTeamMember(teamId, teamMemberId);
        if (target.getRoleName() != null) {
            throw new DuplicateException("이미 역할이 지정된 조원입니다");
        }

        // 앞뒤 공백을 지웁니다. " 백엔드"와 "백엔드"가 다른 역할로 나뉘면 산출물 순번이 끊깁니다.
        target.assignRole(roleName.trim());
        return target;
    }

    // 조장이 역할이 있는 조원의 역할을 바꾸고 이전 역할을 이력으로 남깁니다. 이미 올린 산출물은 자기 역할명을 갖고 있어서 그대로입니다.
    @Transactional
    public TeamMember changeRole(Long teamId, Long teamMemberId, String roleName, Long memberId) {
        Team team = findTeam(teamId);
        teamAccessChecker.checkLeader(team, memberId);
        assignmentPhaseChecker.checkRecording(team.getAssignment());
        TeamMember target = findTeamMember(teamId, teamMemberId);
        if (target.getRoleName() == null) {
            throw new InvalidInputException("아직 역할이 지정되지 않은 조원입니다");
        }

        // assignRole과 같이 앞뒤 공백을 지운 뒤에 지금 역할과 비교합니다. 같으면 바뀐 것이 없어 이력을 남기지 않습니다.
        String newRoleName = roleName.trim();
        if (newRoleName.equals(target.getRoleName())) {
            throw new InvalidInputException("지금과 같은 역할입니다");
        }

        // 역할을 덮어쓰면 이전 값이 사라지므로, 이력을 먼저 저장합니다.
        roleHistoryRepository.save(new RoleHistory(target, target.getRoleName()));
        target.assignRole(newRoleName);
        return target;
    }

    // 한 조원의 역할 이력을 최근에 바뀐 것부터 찾습니다. 그 조의 조원과 그 과제를 만든 교수가 볼 수 있습니다.
    public List<RoleHistory> findRoleHistories(Long teamId, Long teamMemberId, Long memberId) {
        Team team = findTeam(teamId);
        teamAccessChecker.checkTeamAccess(team, memberId);
        TeamMember target = findTeamMember(teamId, teamMemberId);
        return roleHistoryRepository.findByTeamMemberIdOrderByIdDesc(target.getId());
    }

    // 과제를 만든 교수가 같은 조의 다른 조원을 새 조장으로 바꾸고 이력을 남깁니다. 이전 조장은 일반 조원으로 남고 역할과 기록은 그대로입니다.
    @Transactional
    public TeamMember changeLeader(Long teamId, Long newLeaderId, Long memberId) {
        // 교체 요청 두 개가 겹치면 조장이 둘이 될 수 있습니다. 조 행을 잠가 한 번에 하나씩 처리합니다. 잠금은 트랜잭션이 끝나면 풀립니다.
        Team team = teamRepository.findWithLockById(teamId)
                .orElseThrow(() -> new NotFoundException("조가 없습니다. id=" + teamId));
        if (!teamAccessChecker.isProfessorOf(team.getAssignment(), memberId)) {
            throw new ForbiddenException("과제를 만든 교수만 조장을 바꿀 수 있습니다");
        }
        assignmentPhaseChecker.checkRecording(team.getAssignment());

        TeamMember newLeader = findTeamMember(teamId, newLeaderId);
        if (newLeader.isLeader()) {
            throw new InvalidInputException("이미 이 조의 조장입니다");
        }
        TeamMember previousLeader = teamMemberRepository.findByTeamIdAndLeaderTrue(teamId)
                .orElseThrow(() -> new NotFoundException("조장이 없습니다. teamId=" + teamId));

        previousLeader.stepDownAsLeader();
        newLeader.becomeLeader();
        leaderHistoryRepository.save(new LeaderHistory(team, previousLeader, newLeader));
        return newLeader;
    }

    // 한 조의 조장 교체 이력을 최근에 바뀐 것부터 찾습니다. 그 조의 조원과 그 과제를 만든 교수가 볼 수 있습니다.
    public List<LeaderHistory> findLeaderHistories(Long teamId, Long memberId) {
        Team team = findById(teamId, memberId);
        return leaderHistoryRepository.findByTeamIdOrderByIdDesc(team.getId());
    }

    // 그 조의 조원을 찾습니다. 없거나 다른 조의 조원이면 NotFoundException을 던집니다.
    private TeamMember findTeamMember(Long teamId, Long teamMemberId) {
        return teamMemberRepository.findById(teamMemberId)
                .filter(teamMember -> teamMember.getTeam().getId().equals(teamId))
                .orElseThrow(() -> new NotFoundException("이 조의 조원이 아닙니다. id=" + teamMemberId));
    }

    // 조를 찾습니다. 없으면 NotFoundException을 던집니다.
    private Team findTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("조가 없습니다. id=" + teamId));
    }
}
