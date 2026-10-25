package com.footprint.backend.config;

import com.footprint.backend.entity.Assignment;
import com.footprint.backend.entity.Member;
import com.footprint.backend.entity.MemberType;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.repository.AssignmentRepository;
import com.footprint.backend.repository.MemberRepository;
import com.footprint.backend.repository.TeamMemberRepository;
import com.footprint.backend.repository.TeamRepository;
import com.footprint.backend.service.StorageService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

// 서버가 시작할 때 시연용 시작 데이터를 넣습니다. 계정과 조 구성 표는 backend/README.md에 있습니다.
// ddl-auto: create라서 실행할 때마다 테이블이 비워진 뒤 다시 들어갑니다.
@Component
public class DataInitializer implements CommandLineRunner {

    // 시작 계정 전원이 같은 비밀번호를 씁니다. 개발용입니다.
    private static final String DEV_PASSWORD = "pass1234";

    private final MemberRepository memberRepository;
    private final AssignmentRepository assignmentRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;

    public DataInitializer(MemberRepository memberRepository, AssignmentRepository assignmentRepository,
                           TeamRepository teamRepository, TeamMemberRepository teamMemberRepository,
                           PasswordEncoder passwordEncoder, StorageService storageService) {
        this.memberRepository = memberRepository;
        this.assignmentRepository = assignmentRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.storageService = storageService;
    }

    // MinIO 버킷을 준비하고, 회원 11명과 과제 1개, 조 2개를 저장합니다.
    @Override
    public void run(String... args) {
        // 산출물 파일을 넣을 버킷이 없으면 만듭니다. 이미 있으면 그대로 둡니다(데이터베이스와 달리 서버를 다시 켜도 비워지지 않습니다).
        storageService.createBucketIfMissing();

        Member professor = saveMember("professor@example.com", "이교수", MemberType.PROFESSOR);

        Member student01 = saveMember("student01@example.com", "김민준", MemberType.STUDENT);
        Member student02 = saveMember("student02@example.com", "이서연", MemberType.STUDENT);
        Member student03 = saveMember("student03@example.com", "박도윤", MemberType.STUDENT);
        Member student04 = saveMember("student04@example.com", "최서윤", MemberType.STUDENT);
        Member student05 = saveMember("student05@example.com", "정하준", MemberType.STUDENT);
        Member student06 = saveMember("student06@example.com", "강지우", MemberType.STUDENT);
        Member student07 = saveMember("student07@example.com", "조은우", MemberType.STUDENT);
        Member student08 = saveMember("student08@example.com", "윤하은", MemberType.STUDENT);
        Member student09 = saveMember("student09@example.com", "장시우", MemberType.STUDENT);
        Member student10 = saveMember("student10@example.com", "임지아", MemberType.STUDENT);

        Assignment assignment = assignmentRepository.save(
                new Assignment("발자국 시연 과제", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 12, 11), professor));

        // 목록의 첫 번째 학생이 조장입니다. 2조는 "같은 역할을 여러 조원이 맡을 수 있다"는 규칙을 보이려고 백엔드가 2명입니다.
        saveTeam(assignment, "1조", "demo-team-1",
                List.of(student01, student02, student03, student04, student05),
                List.of("기획", "백엔드", "프론트엔드", "디자인", "발표"));
        saveTeam(assignment, "2조", "demo-team-2",
                List.of(student06, student07, student08, student09, student10),
                List.of("기획", "백엔드", "백엔드", "프론트엔드", "발표"));
    }

    // 비밀번호를 BCrypt로 바꿔서 회원을 저장합니다.
    private Member saveMember(String email, String name, MemberType memberType) {
        return memberRepository.save(new Member(email, passwordEncoder.encode(DEV_PASSWORD), name, memberType));
    }

    // 조를 초대 코드와 함께 저장하고 조원을 역할과 함께 등록합니다. members와 roleNames는 같은 순서로 짝을 이룹니다.
    // 시작 데이터의 초대 코드는 시험하기 쉽게 읽을 수 있는 값으로 둡니다. 조장이 다시 발급하면 무작위 값(UUID)으로 바뀝니다.
    private void saveTeam(Assignment assignment, String name, String inviteCode, List<Member> members, List<String> roleNames) {
        Team team = new Team(assignment, name);
        team.changeInviteCode(inviteCode);
        teamRepository.save(team);
        for (int i = 0; i < members.size(); i++) {
            boolean leader = (i == 0);
            TeamMember teamMember = new TeamMember(team, members.get(i), leader);
            teamMember.assignRole(roleNames.get(i));
            teamMemberRepository.save(teamMember);
        }
    }
}
