package com.footprint.backend;

import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.repository.TeamMemberRepository;
import com.footprint.backend.repository.TeamRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 과제 생성, 초대, 합류, 역할 지정 규칙을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// @Transactional: 테스트가 끝나면 데이터베이스 변경을 되돌립니다. 그래서 테스트끼리 서로 영향을 주지 않습니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeamRuleTests {

    private final TestApi api;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    TeamRuleTests(MockMvc mockMvc, TeamRepository teamRepository, TeamMemberRepository teamMemberRepository) {
        this.api = new TestApi(mockMvc);
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    // 교수가 과제를 만들면 조장 이메일 순서대로 "1조", "2조"가 생깁니다.
    @Test
    void professorCreatesAssignmentWithTeams() throws Exception {
        String token = api.login("professor@example.com");

        String response = api.post("/assignments", token, assignmentBody("2026-11-02", "2026-12-11",
                        "\"student01@example.com\", \"student06@example.com\""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int assignmentId = JsonPath.read(response, "$.id");
        api.get("/assignments/" + assignmentId, token)
                .andExpect(jsonPath("$.teams[0].name").value("1조"))
                .andExpect(jsonPath("$.teams[0].leaderName").value("김민준"))
                .andExpect(jsonPath("$.teams[1].name").value("2조"));
    }

    // 학생은 과제를 만들 수 없습니다.
    @Test
    void studentCannotCreateAssignment() throws Exception {
        String body = assignmentBody("2026-11-02", "2026-12-11", "\"student02@example.com\"");

        TestApi.expectError(api.post("/assignments", api.login("student01@example.com"), body), 403, "FORBIDDEN");
    }

    // 종료일이 시작일보다 앞이거나, 조장 이메일이 규칙에 맞지 않으면 400입니다.
    @Test
    void invalidAssignmentIsRejected() throws Exception {
        String token = api.login("professor@example.com");
        String[] invalidBodies = {
                assignmentBody("2026-12-11", "2026-11-02", "\"student01@example.com\""),
                assignmentBody("2026-11-02", "2026-12-11", ""),
                assignmentBody("2026-11-02", "2026-12-11", "\"student01@example.com\", \"student01@example.com\""),
                assignmentBody("2026-11-02", "2026-12-11", "\"nobody@example.com\""),
                assignmentBody("2026-11-02", "2026-12-11", "\"professor@example.com\"")
        };

        for (String body : invalidBodies) {
            TestApi.expectError(api.post("/assignments", token, body), 400, "INVALID_INPUT");
        }
    }

    // 초대 코드는 조장만 봅니다. 같은 조의 조원과 교수는 403입니다.
    @Test
    void onlyLeaderHandlesInviteCode() throws Exception {
        String path = "/teams/" + seedTeam().getId() + "/invite-code";

        api.get(path, api.login("student01@example.com")).andExpect(status().isOk());
        TestApi.expectError(api.get(path, api.login("student02@example.com")), 403, "FORBIDDEN");
        TestApi.expectError(api.post(path, api.login("student02@example.com"), ""), 403, "FORBIDDEN");
        TestApi.expectError(api.post(path, api.login("professor@example.com"), ""), 403, "FORBIDDEN");
    }

    // 초대 코드를 다시 발급하면 이전 코드로는 합류할 수 없고 새 코드로는 합류합니다.
    @Test
    void reissuedInviteCodeReplacesOldOne() throws Exception {
        int teamId = createAssignmentAndGetTeamId("student01@example.com");
        String leader = api.login("student01@example.com");
        String oldCode = issueInviteCode(teamId, leader);
        String newCode = issueInviteCode(teamId, leader);

        String joiner = api.login("student02@example.com");
        TestApi.expectError(api.post("/teams/join", joiner, joinBody(oldCode)), 404, "NOT_FOUND");
        api.post("/teams/join", joiner, joinBody(newCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(teamId));
    }

    // 이미 그 조의 조원이면 다시 합류해도 200으로 같은 조를 받습니다.
    @Test
    void joiningSameTeamAgainIsOk() throws Exception {
        api.post("/teams/join", api.login("student02@example.com"), joinBody("demo-team-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(seedTeam().getId()));
    }

    // 한 학생은 같은 과제에서 한 조에만 참여합니다.
    @Test
    void joiningAnotherTeamOfSameAssignmentIsDuplicate() throws Exception {
        TestApi.expectError(api.post("/teams/join", api.login("student02@example.com"), joinBody("demo-team-2")),
                409, "DUPLICATE");
    }

    // 교수는 조에 합류할 수 없고, 없는 코드는 404입니다.
    @Test
    void professorAndUnknownCodeCannotJoin() throws Exception {
        TestApi.expectError(api.post("/teams/join", api.login("professor@example.com"), joinBody("demo-team-1")),
                403, "FORBIDDEN");
        TestApi.expectError(api.post("/teams/join", api.login("student02@example.com"), joinBody("no-such-code")),
                404, "NOT_FOUND");
    }

    // 조장이 역할 없는 조원에게 역할을 지정합니다. 앞뒤 공백은 지우고, 같은 역할을 여러 조원이 맡을 수 있습니다.
    @Test
    void leaderAssignsRole() throws Exception {
        int teamId = createAssignmentAndGetTeamId("student01@example.com");
        String leader = api.login("student01@example.com");
        String code = issueInviteCode(teamId, leader);
        api.post("/teams/join", api.login("student02@example.com"), joinBody(code)).andExpect(status().isOk());
        TeamMember leaderRecord = teamMemberRepository.findByTeamIdOrderByIdAsc((long) teamId).get(0);
        TeamMember joined = teamMemberRepository.findByTeamIdOrderByIdAsc((long) teamId).get(1);

        api.post(rolePath(teamId, leaderRecord), leader, roleBody("  백엔드  "))
                .andExpect(jsonPath("$.roleName").value("백엔드"));
        api.post(rolePath(teamId, joined), leader, roleBody("백엔드"))
                .andExpect(status().isOk());
    }

    // 조장이 아니면 역할을 지정할 수 없습니다. 교수도 마찬가지입니다.
    @Test
    void onlyLeaderAssignsRole() throws Exception {
        int teamId = createAssignmentAndGetTeamId("student01@example.com");
        TeamMember leaderRecord = teamMemberRepository.findByTeamIdOrderByIdAsc((long) teamId).get(0);

        TestApi.expectError(api.post(rolePath(teamId, leaderRecord), api.login("professor@example.com"), roleBody("기획")),
                403, "FORBIDDEN");
    }

    // 이미 역할이 있으면 409, 역할명이 비었으면 400, 다른 조의 조원이면 404입니다.
    @Test
    void invalidRoleAssignmentIsRejected() throws Exception {
        Team team = seedTeam();
        String leader = api.login("student01@example.com");
        TeamMember withRole = teamMemberRepository.findByTeamIdOrderByIdAsc(team.getId()).get(1);
        Team otherTeam = teamRepository.findByInviteCode("demo-team-2").orElseThrow();
        TeamMember otherTeamMember = teamMemberRepository.findByTeamIdOrderByIdAsc(otherTeam.getId()).get(1);
        String path = "/teams/" + team.getId() + "/members/";

        TestApi.expectError(api.post(path + withRole.getId() + "/role", leader, roleBody(" ")), 400, "INVALID_INPUT");
        TestApi.expectError(api.post(path + otherTeamMember.getId() + "/role", leader, roleBody("기획")), 404, "NOT_FOUND");
        TestApi.expectError(api.post(path + withRole.getId() + "/role", leader, roleBody("기획")), 409, "DUPLICATE");
    }

    // 시작 데이터의 1조를 찾습니다.
    private Team seedTeam() {
        return teamRepository.findByInviteCode("demo-team-1").orElseThrow();
    }

    // 조장 한 명으로 새 과제를 만들고 그 조의 id를 돌려줍니다. 시작 데이터와 달리 조장 말고 조원이 없고 역할도 비어 있습니다.
    private int createAssignmentAndGetTeamId(String leaderEmail) throws Exception {
        String token = api.login("professor@example.com");
        String created = api.post("/assignments", token, assignmentBody("2026-11-02", "2026-12-11", "\"" + leaderEmail + "\""))
                .andReturn().getResponse().getContentAsString();
        int assignmentId = JsonPath.read(created, "$.id");
        String detail = api.get("/assignments/" + assignmentId, token).andReturn().getResponse().getContentAsString();
        return JsonPath.read(detail, "$.teams[0].id");
    }

    // 초대 코드를 새로 발급받아 돌려줍니다.
    private String issueInviteCode(int teamId, String leaderToken) throws Exception {
        String response = api.post("/teams/" + teamId + "/invite-code", leaderToken, "")
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.inviteCode");
    }

    private String rolePath(int teamId, TeamMember teamMember) {
        return "/teams/" + teamId + "/members/" + teamMember.getId() + "/role";
    }

    private String assignmentBody(String startDate, String endDate, String leaderEmails) {
        return """
                { "title": "시험 과제", "startDate": "%s", "endDate": "%s", "leaderEmails": [%s] }
                """.formatted(startDate, endDate, leaderEmails);
    }

    private String joinBody(String inviteCode) {
        return """
                { "inviteCode": "%s" }
                """.formatted(inviteCode);
    }

    private String roleBody(String roleName) {
        return """
                { "roleName": "%s" }
                """.formatted(roleName);
    }
}
