package com.footprint.backend;

import com.footprint.backend.entity.Member;
import com.footprint.backend.entity.MemberType;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.repository.MemberRepository;
import com.footprint.backend.repository.TeamMemberRepository;
import com.footprint.backend.repository.TeamRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 조장 교체와 교체 이력 규칙을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 시작 데이터의 1조(조장 student01 기획, 조원 student02 백엔드)와 2조(student06)를 씁니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LeaderChangeRuleTests {

    private final TestApi api;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    private Long teamId;
    private Long oldLeaderId;
    private Long newLeaderId;
    private String leaderPath;
    private String historiesPath;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    LeaderChangeRuleTests(MockMvc mockMvc, TeamRepository teamRepository, TeamMemberRepository teamMemberRepository,
                          MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.api = new TestApi(mockMvc);
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 시작 데이터의 1조에서 지금 조장(student01)과 새 조장이 될 조원(student02)의 번호를 찾아 둡니다.
    @BeforeEach
    void findSeedTeam() {
        Team team = teamRepository.findByInviteCode("demo-team-1").orElseThrow();
        teamId = team.getId();
        List<TeamMember> teamMembers = teamMemberRepository.findByTeamIdOrderByIdAsc(teamId);
        oldLeaderId = teamMembers.get(0).getId();
        newLeaderId = teamMembers.get(1).getId();
        leaderPath = "/teams/" + teamId + "/leader";
        historiesPath = "/teams/" + teamId + "/leader-histories";
    }

    // 교수가 바꾸면 조장이 한 명 그대로 옮겨 가고, 이전 조장은 역할을 가진 조원으로 남고, 이력이 최근 것부터 쌓입니다.
    @Test
    void professorChangesLeaderAndHistoryRemains() throws Exception {
        String professor = api.login("professor@example.com");

        api.put(leaderPath, professor, leaderBody(newLeaderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(newLeaderId))
                .andExpect(jsonPath("$.name").value("이서연"))
                .andExpect(jsonPath("$.roleName").value("백엔드"))
                .andExpect(jsonPath("$.leader").value(true));

        api.get("/teams/" + teamId + "/members", professor)
                .andExpect(jsonPath("$[?(@.leader == true)]", hasSize(1)))
                .andExpect(jsonPath("$[0].leader").value(false))
                .andExpect(jsonPath("$[0].roleName").value("기획"))
                .andExpect(jsonPath("$[1].leader").value(true));

        api.put(leaderPath, professor, leaderBody(oldLeaderId)).andExpect(status().isOk());

        api.get(historiesPath, professor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].previousLeaderName").value("이서연"))
                .andExpect(jsonPath("$[0].newLeaderName").value("김민준"))
                .andExpect(jsonPath("$[1].previousLeaderName").value("김민준"))
                .andExpect(jsonPath("$[1].newLeaderName").value("이서연"))
                // 서버가 초까지 기록합니다. 다른 기록 시각, 가짜 API와 같은 모양입니다.
                .andExpect(jsonPath("$[0].changedAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));
    }

    // 조장만 할 수 있는 일은 새 조장에게 옮겨 갑니다. 이전 조장은 403이 됩니다.
    @Test
    void leaderPermissionsMoveToNewLeader() throws Exception {
        String oldLeader = api.login("student01@example.com");
        String newLeader = api.login("student02@example.com");

        TestApi.expectError(api.post("/teams/" + teamId + "/invite-code", newLeader, ""), 403, "FORBIDDEN");

        api.put(leaderPath, api.login("professor@example.com"), leaderBody(newLeaderId)).andExpect(status().isOk());

        api.post("/teams/" + teamId + "/invite-code", newLeader, "").andExpect(status().isOk());
        TestApi.expectError(api.post("/teams/" + teamId + "/invite-code", oldLeader, ""), 403, "FORBIDDEN");
        TestApi.expectError(api.put("/teams/" + teamId + "/members/" + newLeaderId + "/role", oldLeader,
                "{ \"roleName\": \"데이터\" }"), 403, "FORBIDDEN");
    }

    // 과제 상세의 조장 이름이 새 조장으로 바뀝니다. 교수와 조원 모두 같은 조장을 봅니다.
    @Test
    void assignmentDetailShowsNewLeader() throws Exception {
        String professor = api.login("professor@example.com");
        Long assignmentId = teamRepository.findById(teamId).orElseThrow().getAssignment().getId();

        api.put(leaderPath, professor, leaderBody(newLeaderId)).andExpect(status().isOk());

        api.get("/assignments/" + assignmentId, professor).andExpect(jsonPath("$.teams[0].leaderName").value("이서연"));
        api.get("/assignments/" + assignmentId, api.login("student03@example.com"))
                .andExpect(jsonPath("$.teams[0].leaderName").value("이서연"));
    }

    // 이전 조장의 기록은 그대로입니다. 산출물이 남아 있고, 역할이 있으니 계속 올릴 수 있습니다.
    @Test
    void oldLeaderKeepsRecords() throws Exception {
        String oldLeader = api.login("student01@example.com");
        String deliverablesPath = "/teams/" + teamId + "/deliverables";
        String before = api.get(deliverablesPath, oldLeader).andReturn().getResponse().getContentAsString();
        List<Object> deliverables = JsonPath.read(before, "$");

        api.put(leaderPath, api.login("professor@example.com"), leaderBody(newLeaderId)).andExpect(status().isOk());

        api.get(deliverablesPath, oldLeader).andExpect(jsonPath("$", hasSize(deliverables.size())));
        MockMultipartFile file = new MockMultipartFile("file", "교체 후.txt", "text/plain", "조장 교체 시험".getBytes(StandardCharsets.UTF_8));
        api.upload(deliverablesPath, oldLeader, file)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleName").value("기획"));
    }

    // 역할이 없는 조원도 조장이 될 수 있습니다. 새 조장이 자기 역할을 지정합니다.
    @Test
    void memberWithoutRoleCanBecomeLeader() throws Exception {
        memberRepository.save(new Member("new-student@example.com", passwordEncoder.encode("pass1234"), "새학생", MemberType.STUDENT));
        String newStudent = api.login("new-student@example.com");
        api.post("/teams/join", newStudent, "{ \"inviteCode\": \"demo-team-1\" }").andExpect(status().isOk());
        Long joinedId = teamMemberRepository.findByTeamIdOrderByIdAsc(teamId).getLast().getId();

        api.put(leaderPath, api.login("professor@example.com"), leaderBody(joinedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleName").isEmpty())
                .andExpect(jsonPath("$.leader").value(true));

        api.post("/teams/" + teamId + "/members/" + joinedId + "/role", newStudent, "{ \"roleName\": \"문서\" }")
                .andExpect(status().isOk());
    }

    // 과제를 만든 교수만 바꿉니다. 조장, 조원, 다른 교수는 403이고 이력이 남지 않습니다.
    @Test
    void onlyItsProfessorChangesLeader() throws Exception {
        memberRepository.save(new Member("other-professor@example.com", passwordEncoder.encode("pass1234"), "다른교수", MemberType.PROFESSOR));

        for (String email : List.of("student01@example.com", "student02@example.com", "other-professor@example.com")) {
            TestApi.expectError(api.put(leaderPath, api.login(email), leaderBody(newLeaderId)), 403, "FORBIDDEN");
        }
        api.get(historiesPath, api.login("professor@example.com")).andExpect(jsonPath("$", hasSize(0)));
    }

    // 없는 조, 없는 조원, 다른 조의 조원을 가리키면 404입니다.
    @Test
    void wrongTeamOrMemberIsNotFound() throws Exception {
        String professor = api.login("professor@example.com");
        Long otherTeamId = teamRepository.findByInviteCode("demo-team-2").orElseThrow().getId();
        Long otherTeamMemberId = teamMemberRepository.findByTeamIdOrderByIdAsc(otherTeamId).get(1).getId();

        TestApi.expectError(api.put("/teams/999999/leader", professor, leaderBody(newLeaderId)), 404, "NOT_FOUND");
        TestApi.expectError(api.put(leaderPath, professor, leaderBody(999999L)), 404, "NOT_FOUND");
        TestApi.expectError(api.put(leaderPath, professor, leaderBody(otherTeamMemberId)), 404, "NOT_FOUND");
    }

    // 이미 조장인 조원을 고르면 400이고 이력이 쌓이지 않습니다. 번호가 빠져도 400입니다.
    @Test
    void invalidTargetIsRejected() throws Exception {
        String professor = api.login("professor@example.com");

        expectBadRequest(api.put(leaderPath, professor, leaderBody(oldLeaderId)), "이미 이 조의 조장입니다");
        TestApi.expectError(api.put(leaderPath, professor, "{ }"), 400, "INVALID_INPUT");

        api.get(historiesPath, professor).andExpect(jsonPath("$", hasSize(0)));
    }

    // 이력은 그 조의 조원과 과제를 만든 교수가 봅니다. 다른 조 학생은 403, 없는 조는 404입니다.
    @Test
    void historiesFollowTeamAccess() throws Exception {
        api.get(historiesPath, api.login("student03@example.com")).andExpect(status().isOk());
        api.get(historiesPath, api.login("professor@example.com")).andExpect(status().isOk());
        TestApi.expectError(api.get(historiesPath, api.login("student06@example.com")), 403, "FORBIDDEN");
        TestApi.expectError(api.get("/teams/999999/leader-histories", api.login("professor@example.com")), 404, "NOT_FOUND");
        TestApi.expectError(api.getWithoutToken(historiesPath), 401, "UNAUTHORIZED");
    }

    // 조장 교체 요청 body를 만듭니다.
    private String leaderBody(Long teamMemberId) {
        return "{ \"teamMemberId\": " + teamMemberId + " }";
    }

    // 400 INVALID_INPUT이고 메시지가 같은지 확인합니다. 400은 이유가 여러 가지라서 메시지까지 봅니다.
    private void expectBadRequest(ResultActions result, String message) throws Exception {
        TestApi.expectError(result, 400, "INVALID_INPUT");
        result.andExpect(jsonPath("$.message").value(message));
    }
}
