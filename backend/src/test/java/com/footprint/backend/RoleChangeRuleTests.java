package com.footprint.backend;

import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.repository.TeamMemberRepository;
import com.footprint.backend.repository.TeamRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
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

// 역할 변경과 역할 이력 규칙을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 시작 데이터의 1조(조장 student01, 조원 student02는 백엔드)와 2조(student06, student07)를 씁니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoleChangeRuleTests {

    private final TestApi api;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    private Long teamId;
    private String rolePath;
    private String historiesPath;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    RoleChangeRuleTests(MockMvc mockMvc, TeamRepository teamRepository, TeamMemberRepository teamMemberRepository) {
        this.api = new TestApi(mockMvc);
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    // 시작 데이터의 1조에서 student02의 역할 변경 주소와 이력 주소를 만들어 둡니다.
    @BeforeEach
    void findSeedTeam() {
        Team team = teamRepository.findByInviteCode("demo-team-1").orElseThrow();
        teamId = team.getId();
        List<TeamMember> teamMembers = teamMemberRepository.findByTeamIdOrderByIdAsc(teamId);
        rolePath = memberPath(teamId, teamMembers.get(1).getId()) + "/role";
        historiesPath = memberPath(teamId, teamMembers.get(1).getId()) + "/role-histories";
    }

    // 조장이 역할을 바꾸면 조원의 역할이 바뀌고, 이전 역할은 최근 것부터 이력으로 남습니다.
    @Test
    void leaderChangesRoleAndHistoryRemains() throws Exception {
        String leader = api.login("student01@example.com");

        api.put(rolePath, leader, roleBody("  데이터  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleName").value("데이터"))
                .andExpect(jsonPath("$.leader").value(false));
        api.put(rolePath, leader, roleBody("문서")).andExpect(status().isOk());

        api.get(historiesPath, leader)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].previousRoleName").value("데이터"))
                .andExpect(jsonPath("$[1].previousRoleName").value("백엔드"))
                // 서버가 초까지 기록합니다. 다른 기록 시각, 가짜 API와 같은 모양입니다.
                .andExpect(jsonPath("$[0].changedAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));
        api.get("/teams/" + teamId + "/members", leader).andExpect(jsonPath("$[1].roleName").value("문서"));
    }

    // 역할이 바뀌어도 이미 올린 산출물은 올릴 때의 역할로 남고, 새로 올리면 새 역할이 복사됩니다.
    @Test
    void uploadedDeliverableKeepsItsRoleName() throws Exception {
        String member = api.login("student02@example.com");
        String deliverablesPath = "/teams/" + teamId + "/deliverables";
        String response = api.upload(deliverablesPath, member, textFile("변경 전.txt"))
                .andExpect(jsonPath("$.roleName").value("백엔드"))
                .andReturn().getResponse().getContentAsString();
        int beforeId = JsonPath.read(response, "$.id");

        api.put(rolePath, api.login("student01@example.com"), roleBody("데이터")).andExpect(status().isOk());

        api.upload(deliverablesPath, member, textFile("변경 후.txt")).andExpect(jsonPath("$.roleName").value("데이터"));
        // 최신순 목록이라 [0]이 방금 올린 파일, [1]이 역할을 바꾸기 전에 올린 파일입니다.
        api.get(deliverablesPath, member)
                .andExpect(jsonPath("$[0].roleName").value("데이터"))
                .andExpect(jsonPath("$[1].id").value(beforeId))
                .andExpect(jsonPath("$[1].roleName").value("백엔드"));
    }

    // 조장만 바꿉니다. 조원, 다른 조 학생, 과제를 만든 교수도 403이고 이력은 남지 않습니다.
    @Test
    void onlyLeaderChangesRole() throws Exception {
        for (String email : List.of("student02@example.com", "student06@example.com", "professor@example.com")) {
            TestApi.expectError(api.put(rolePath, api.login(email), roleBody("데이터")), 403, "FORBIDDEN");
        }
        api.get(historiesPath, api.login("student01@example.com")).andExpect(jsonPath("$", hasSize(0)));
    }

    // 없는 조, 없는 조원, 다른 조의 조원을 가리키면 404입니다.
    @Test
    void changeRoleOfWrongMemberIsNotFound() throws Exception {
        String leader = api.login("student01@example.com");
        Long otherTeamId = teamRepository.findByInviteCode("demo-team-2").orElseThrow().getId();
        Long otherTeamMemberId = teamMemberRepository.findByTeamIdOrderByIdAsc(otherTeamId).get(1).getId();

        TestApi.expectError(api.put(memberPath(999999L, 1L) + "/role", leader, roleBody("데이터")), 404, "NOT_FOUND");
        TestApi.expectError(api.put(memberPath(teamId, 999999L) + "/role", leader, roleBody("데이터")), 404, "NOT_FOUND");
        TestApi.expectError(api.put(memberPath(teamId, otherTeamMemberId) + "/role", leader, roleBody("데이터")), 404, "NOT_FOUND");
    }

    // 지금과 같은 역할(앞뒤 공백을 지운 값)이면 400이고 이력이 쌓이지 않습니다.
    @Test
    void sameRoleNameIsRejected() throws Exception {
        String leader = api.login("student01@example.com");

        expectBadRequest(api.put(rolePath, leader, roleBody(" 백엔드 ")), "지금과 같은 역할입니다");

        api.get(historiesPath, leader).andExpect(jsonPath("$", hasSize(0)));
    }

    // 역할이 없는 조원은 바꿀 수 없습니다. 첫 지정은 POST이고, POST의 409는 그대로입니다.
    @Test
    void firstAssignmentStaysOnPost() throws Exception {
        String leader = api.login("student01@example.com");
        String path = roleOfNewTeamLeader(api.login("professor@example.com"), leader);

        expectBadRequest(api.put(path, leader, roleBody("기획")), "아직 역할이 지정되지 않은 조원입니다");

        api.post(path, leader, roleBody("기획")).andExpect(status().isOk());
        TestApi.expectError(api.post(path, leader, roleBody("문서")), 409, "DUPLICATE");
        api.put(path, leader, roleBody("문서")).andExpect(status().isOk());
    }

    // 역할명이 비었거나 255자를 넘으면 400입니다.
    @Test
    void invalidRoleNameIsRejected() throws Exception {
        String leader = api.login("student01@example.com");

        TestApi.expectError(api.put(rolePath, leader, roleBody("   ")), 400, "INVALID_INPUT");
        TestApi.expectError(api.put(rolePath, leader, roleBody("가".repeat(256))), 400, "INVALID_INPUT");
    }

    // 이력은 그 조의 조원과 과제를 만든 교수가 봅니다. 다른 조 학생은 403, 다른 조의 조원 번호는 404입니다.
    @Test
    void historiesFollowTeamAccess() throws Exception {
        Long otherTeamId = teamRepository.findByInviteCode("demo-team-2").orElseThrow().getId();
        Long otherTeamMemberId = teamMemberRepository.findByTeamIdOrderByIdAsc(otherTeamId).get(0).getId();

        api.get(historiesPath, api.login("student03@example.com")).andExpect(status().isOk());
        api.get(historiesPath, api.login("professor@example.com")).andExpect(status().isOk());
        TestApi.expectError(api.get(historiesPath, api.login("student06@example.com")), 403, "FORBIDDEN");
        TestApi.expectError(api.get(memberPath(teamId, otherTeamMemberId) + "/role-histories", api.login("student01@example.com")),
                404, "NOT_FOUND");
        TestApi.expectError(api.getWithoutToken(historiesPath), 401, "UNAUTHORIZED");
    }

    // student01을 조장으로 새 과제를 만들고, 아직 역할이 없는 그 조장의 역할 주소를 돌려줍니다.
    private String roleOfNewTeamLeader(String professor, String leader) throws Exception {
        String created = api.post("/assignments", professor, """
                        { "title": "시험 과제", "startDate": "2026-11-02", "endDate": "2026-12-11",
                          "leaderEmails": ["student01@example.com"] }
                        """)
                .andReturn().getResponse().getContentAsString();
        int createdId = JsonPath.read(created, "$.id");
        String detail = api.get("/assignments/" + createdId, professor).andReturn().getResponse().getContentAsString();
        int newTeamId = JsonPath.read(detail, "$.teams[0].id");
        String members = api.get("/teams/" + newTeamId + "/members", leader).andReturn().getResponse().getContentAsString();
        int teamMemberId = JsonPath.read(members, "$[0].id");
        return "/teams/" + newTeamId + "/members/" + teamMemberId + "/role";
    }

    // 조원 한 명의 주소를 만듭니다.
    private String memberPath(Long teamId, Long teamMemberId) {
        return "/teams/" + teamId + "/members/" + teamMemberId;
    }

    // 역할 변경 요청 body를 만듭니다.
    private String roleBody(String roleName) {
        return "{ \"roleName\": \"" + roleName + "\" }";
    }

    // 산출물 업로드에 쓸 짧은 글자 파일을 만듭니다.
    private MockMultipartFile textFile(String fileName) {
        return new MockMultipartFile("file", fileName, "text/plain", "역할 변경 시험".getBytes(StandardCharsets.UTF_8));
    }

    // 400 INVALID_INPUT이고 메시지가 같은지 확인합니다. 400은 이유가 여러 가지라서 메시지까지 봅니다.
    private void expectBadRequest(ResultActions result, String message) throws Exception {
        TestApi.expectError(result, 400, "INVALID_INPUT");
        result.andExpect(jsonPath("$.message").value(message));
    }
}
