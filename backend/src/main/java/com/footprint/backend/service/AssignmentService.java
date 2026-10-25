package com.footprint.backend.service;

import com.footprint.backend.entity.Assignment;
import com.footprint.backend.entity.Member;
import com.footprint.backend.entity.MemberType;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.exception.ForbiddenException;
import com.footprint.backend.exception.InvalidInputException;
import com.footprint.backend.exception.NotFoundException;
import com.footprint.backend.repository.AssignmentRepository;
import com.footprint.backend.repository.MemberRepository;
import com.footprint.backend.repository.TeamMemberRepository;
import com.footprint.backend.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 과제를 만들고 조회합니다. 만들 때는 과제, 조, 조장을 한 번에 저장합니다.
@Service
public class AssignmentService {

    // 회원을 읽는 길이 둘입니다. 로그인한 회원이 없으면 404(MemberService), 조장 이메일이 없으면 입력이 틀린 것이라 400(MemberRepository에서 직접)입니다.
    private final MemberService memberService;
    private final MemberRepository memberRepository;
    private final AssignmentRepository assignmentRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamAccessChecker teamAccessChecker;

    public AssignmentService(MemberService memberService, MemberRepository memberRepository,
                             AssignmentRepository assignmentRepository, TeamRepository teamRepository,
                             TeamMemberRepository teamMemberRepository, TeamAccessChecker teamAccessChecker) {
        this.memberService = memberService;
        this.memberRepository = memberRepository;
        this.assignmentRepository = assignmentRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamAccessChecker = teamAccessChecker;
    }

    // 과제를 만들고, leaderEmails 순서대로 "1조", "2조"…와 각 조의 조장을 저장합니다.
    // @Transactional: 과제·조·조장 저장이 하나의 트랜잭션입니다. 중간에 예외가 나면 앞에서 저장한 것도 모두 취소됩니다.
    @Transactional
    public Assignment create(Long memberId, String title, LocalDate startDate, LocalDate endDate, List<String> leaderEmails) {
        Member professor = memberService.findById(memberId);
        checkProfessor(professor);

        if (endDate.isBefore(startDate)) {
            throw new InvalidInputException("종료일이 시작일보다 앞입니다");
        }

        // 저장하기 전에 조장 이메일을 모두 검사합니다. 검사에 걸리면 아무것도 저장되지 않습니다.
        List<Member> leaders = findLeaders(leaderEmails);

        Assignment assignment = assignmentRepository.save(new Assignment(title, startDate, endDate, professor));
        for (int i = 0; i < leaders.size(); i++) {
            Team team = teamRepository.save(new Team(assignment, (i + 1) + "조"));
            // 조장도 그 조의 조원으로 함께 등록합니다. 역할명은 역할 지정(T-2-1)에서 넣습니다.
            teamMemberRepository.save(new TeamMember(team, leaders.get(i), true));
        }
        return assignment;
    }

    // 로그인한 회원이 볼 수 있는 과제 목록입니다. 교수는 자기가 만든 과제, 학생은 조원으로 참여한 과제입니다.
    public List<Assignment> findAllForMember(Long memberId) {
        Member member = memberService.findById(memberId);
        if (member.getMemberType() == MemberType.PROFESSOR) {
            return assignmentRepository.findByProfessorIdOrderByIdDesc(memberId);
        }
        // 조 참여 기록에서 조 → 과제로 따라갑니다. 한 학생은 한 과제에서 한 조에만 있으므로 같은 과제가 두 번 나오지 않습니다.
        return teamMemberRepository.findByMemberIdOrderByIdDesc(memberId).stream()
                .map(teamMember -> teamMember.getTeam().getAssignment())
                .toList();
    }

    // 과제 하나를 찾습니다. 과제를 만든 교수나 그 과제의 조원이 아니면 ForbiddenException을 던집니다.
    public Assignment findById(Long assignmentId, Long memberId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("과제가 없습니다. id=" + assignmentId));
        teamAccessChecker.checkAssignmentAccess(assignment, memberId);
        return assignment;
    }

    // 과제 상세에 보여 줄 조장 기록 목록입니다. 교수는 조 전부, 학생은 자기 조 하나만 받습니다.
    // 조마다 조장이 한 명이라 조장 기록 하나가 조 하나입니다(조 이름과 조장 이름을 함께 꺼낼 수 있습니다).
    public List<TeamMember> findVisibleTeamLeaders(Assignment assignment, Long memberId) {
        if (teamAccessChecker.isProfessorOf(assignment, memberId)) {
            return teamMemberRepository.findByTeamAssignmentIdAndLeaderTrueOrderByTeamId(assignment.getId());
        }
        TeamMember myTeamMember = teamAccessChecker.checkAssignmentMember(assignment, memberId);
        Long myTeamId = myTeamMember.getTeam().getId();
        TeamMember leader = teamMemberRepository.findByTeamIdAndLeaderTrue(myTeamId)
                .orElseThrow(() -> new NotFoundException("조장이 없습니다. teamId=" + myTeamId));
        return List.of(leader);
    }

    // 교수가 아니면 ForbiddenException을 던집니다. 과제는 교수만 만듭니다.
    private void checkProfessor(Member member) {
        if (member.getMemberType() != MemberType.PROFESSOR) {
            throw new ForbiddenException("교수만 과제를 만들 수 있습니다");
        }
    }

    // 조장 이메일마다 회원을 찾아 같은 순서의 목록으로 돌려줍니다. 규칙에 맞지 않으면 InvalidInputException을 던집니다.
    private List<Member> findLeaders(List<String> leaderEmails) {
        List<Member> leaders = new ArrayList<>();
        // 이미 검사한 이메일입니다. 한 학생을 같은 과제의 두 조에 조장으로 지정하지 못하게 합니다.
        Set<String> checkedEmails = new HashSet<>();

        for (String email : leaderEmails) {
            if (checkedEmails.contains(email)) {
                throw new InvalidInputException("같은 이메일을 두 조의 조장으로 지정할 수 없습니다: " + email);
            }
            checkedEmails.add(email);

            Member leader = memberRepository.findByEmail(email)
                    .orElseThrow(() -> new InvalidInputException("가입하지 않은 이메일입니다: " + email));
            if (leader.getMemberType() != MemberType.STUDENT) {
                throw new InvalidInputException("학생이 아닌 회원은 조장이 될 수 없습니다: " + email);
            }
            leaders.add(leader);
        }
        return leaders;
    }
}
