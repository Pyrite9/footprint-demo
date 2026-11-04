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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 기여 항목 규칙을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 시작 데이터의 1조(student01 조장, student02~05 조원)를 씁니다. 1조에는 시작 기여 항목이 1건 있습니다.
// @Transactional: 테스트가 끝나면 데이터베이스 변경을 되돌립니다. 그래서 테스트끼리 서로 영향을 주지 않습니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContributionRuleTests {

    private final TestApi api;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    private Long teamId;
    private String contributionsPath;
    private List<TeamMember> members;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    ContributionRuleTests(MockMvc mockMvc, TeamRepository teamRepository, TeamMemberRepository teamMemberRepository) {
        this.api = new TestApi(mockMvc);
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    // 시작 데이터의 1조와 조원을 찾아 둡니다. members의 순서는 student01, student02, ...입니다.
    @BeforeEach
    void findSeedTeam() {
        Team team = teamRepository.findByInviteCode("demo-team-1").orElseThrow();
        teamId = team.getId();
        contributionsPath = "/teams/" + teamId + "/contributions";
        members = teamMemberRepository.findByTeamIdOrderByIdAsc(teamId);
    }

    // 조장이 등록하면 보고 전 상태로 저장되고, 등록 시각은 서버가 넣습니다. 보낸 시각은 무시합니다.
    @Test
    void leaderCreatesContribution() throws Exception {
        String leader = api.login("student01@example.com");
        String body = """
                { "title": "  회의 진행  ", "teamMemberId": %d, "createdAt": "2000-01-01T00:00:00" }
                """.formatted(members.get(1).getId());

        api.post(contributionsPath, leader, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("회의 진행"))
                .andExpect(jsonPath("$.assigneeMemberId").value(members.get(1).getMember().getId()))
                .andExpect(jsonPath("$.assigneeName").value("이서연"))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")))
                .andExpect(jsonPath("$.createdAt").value(not(startsWith("2000"))))
                .andExpect(jsonPath("$.reportMemo").isEmpty())
                .andExpect(jsonPath("$.reportedAt").isEmpty())
                .andExpect(jsonPath("$.canceledAt").isEmpty());
    }

    // 조장 자신도 담당이 될 수 있습니다.
    @Test
    void leaderCanBeAssignee() throws Exception {
        api.post(contributionsPath, api.login("student01@example.com"), createBody("자료 취합", members.get(0).getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeName").value("김민준"));
    }

    // 조장만 등록합니다. 조원, 다른 조 학생, 교수는 403입니다.
    @Test
    void onlyLeaderCreates() throws Exception {
        String body = createBody("회의 진행", members.get(1).getId());

        for (String email : List.of("student02@example.com", "student06@example.com", "professor@example.com")) {
            TestApi.expectError(api.post(contributionsPath, api.login(email), body), 403, "FORBIDDEN");
        }
    }

    // 담당은 이 조의 조원이어야 합니다. 다른 조의 조원과 없는 번호는 404입니다.
    @Test
    void assigneeMustBeInTeam() throws Exception {
        String leader = api.login("student01@example.com");
        Long otherTeamId = teamRepository.findByInviteCode("demo-team-2").orElseThrow().getId();
        Long otherTeamMemberId = teamMemberRepository.findByTeamIdOrderByIdAsc(otherTeamId).get(0).getId();

        TestApi.expectError(api.post(contributionsPath, leader, createBody("회의 진행", otherTeamMemberId)), 404, "NOT_FOUND");
        TestApi.expectError(api.post(contributionsPath, leader, createBody("회의 진행", 999999L)), 404, "NOT_FOUND");
    }

    // 항목명이 비었거나 255자를 넘거나, 담당이 빠지면 400입니다. 없는 조는 404입니다.
    @Test
    void wrongInputIsRejected() throws Exception {
        String leader = api.login("student01@example.com");
        Long assigneeId = members.get(1).getId();

        TestApi.expectError(api.post(contributionsPath, leader, createBody("   ", assigneeId)), 400, "INVALID_INPUT");
        TestApi.expectError(api.post(contributionsPath, leader, createBody("가".repeat(256), assigneeId)), 400, "INVALID_INPUT");
        TestApi.expectError(api.post(contributionsPath, leader, "{ \"title\": \"회의 진행\" }"), 400, "INVALID_INPUT");
        TestApi.expectError(api.post("/teams/999999/contributions", leader, createBody("회의 진행", assigneeId)), 404, "NOT_FOUND");
    }

    // 조원과 그 과제를 만든 교수는 목록을 봅니다. 최근에 등록한 것이 먼저입니다. 다른 조 학생은 403입니다.
    @Test
    void membersAndProfessorSeeNewestFirst() throws Exception {
        api.post(contributionsPath, api.login("student01@example.com"), createBody("회의 진행", members.get(1).getId()))
                .andExpect(status().isOk());

        for (String email : List.of("student01@example.com", "student03@example.com", "professor@example.com")) {
            api.get(contributionsPath, api.login(email))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].title").value("회의 진행"))
                    .andExpect(jsonPath("$[1].title").value("회의록 정리"));
        }
        TestApi.expectError(api.get(contributionsPath, api.login("student06@example.com")), 403, "FORBIDDEN");
    }

    // 평가 단계에서는 등록을 거부합니다. 권한 검사가 먼저이고, 목록은 그대로 볼 수 있습니다.
    @Test
    void evaluationPhaseBlocksCreate() throws Exception {
        Team evaluationTeam = teamRepository.findByInviteCode("demo-evaluation-1").orElseThrow();
        String path = "/teams/" + evaluationTeam.getId() + "/contributions";
        Long assigneeId = teamMemberRepository.findByTeamIdOrderByIdAsc(evaluationTeam.getId()).get(1).getId();
        String body = createBody("회의 진행", assigneeId);

        ResultActions blocked = api.post(path, api.login("student01@example.com"), body);
        TestApi.expectError(blocked, 400, "INVALID_INPUT");
        blocked.andExpect(jsonPath("$.message").value("평가 단계에서는 조 운영과 과정 기록을 바꿀 수 없습니다"));
        TestApi.expectError(api.post(path, api.login("student02@example.com"), body), 403, "FORBIDDEN");
        api.get(path, api.login("student02@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    // 담당 조원이 보고하면 메모와 시각이 저장되고, 목록에도 나옵니다. 보낸 시각은 무시합니다.
    @Test
    void assigneeReports() throws Exception {
        String member = api.login("student02@example.com");
        int id = createAssignedToStudent02();
        String body = "{ \"memo\": \"  회의를 진행했습니다  \", \"reportedAt\": \"2000-01-01T00:00:00\" }";

        api.post("/contributions/" + id + "/report", member, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.reportMemo").value("회의를 진행했습니다"))
                .andExpect(jsonPath("$.reportedAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")))
                .andExpect(jsonPath("$.reportedAt").value(not(startsWith("2000"))))
                .andExpect(jsonPath("$.canceledAt").isEmpty());

        api.get(contributionsPath, api.login("professor@example.com"))
                .andExpect(jsonPath("$[0].reportMemo").value("회의를 진행했습니다"));
    }

    // 담당 조원 본인만 보고합니다. 조장, 다른 조원, 다른 조 학생, 교수는 403이고 항목은 그대로입니다.
    @Test
    void onlyAssigneeReports() throws Exception {
        int id = createAssignedToStudent02();

        for (String email : List.of("student01@example.com", "student03@example.com", "student06@example.com", "professor@example.com")) {
            TestApi.expectError(api.post("/contributions/" + id + "/report", api.login(email), reportBody("끝냈습니다")), 403, "FORBIDDEN");
        }
        api.get(contributionsPath, api.login("student01@example.com")).andExpect(jsonPath("$[0].reportedAt").isEmpty());
    }

    // 한 번만 보고합니다. 다시 보고하면 409이고 처음 메모는 그대로입니다.
    @Test
    void reportOnlyOnce() throws Exception {
        String member = api.login("student02@example.com");
        int id = createAssignedToStudent02();
        api.post("/contributions/" + id + "/report", member, reportBody("처음 메모")).andExpect(status().isOk());

        ResultActions again = api.post("/contributions/" + id + "/report", member, reportBody("고친 메모"));

        TestApi.expectError(again, 409, "DUPLICATE");
        again.andExpect(jsonPath("$.message").value("이미 완료 보고한 항목입니다"));
        api.get(contributionsPath, member).andExpect(jsonPath("$[0].reportMemo").value("처음 메모"));
    }

    // 메모는 필수이고 2000자까지입니다. 없는 항목은 404, 번호가 숫자가 아니면 400입니다.
    @Test
    void wrongReportIsRejected() throws Exception {
        String member = api.login("student02@example.com");
        int id = createAssignedToStudent02();
        String path = "/contributions/" + id + "/report";

        TestApi.expectError(api.post(path, member, reportBody("   ")), 400, "INVALID_INPUT");
        TestApi.expectError(api.post(path, member, "{}"), 400, "INVALID_INPUT");
        TestApi.expectError(api.post(path, member, reportBody("가".repeat(2001))), 400, "INVALID_INPUT");
        TestApi.expectError(api.post("/contributions/999999/report", member, reportBody("끝냈습니다")), 404, "NOT_FOUND");
        TestApi.expectError(api.post("/contributions/abc/report", member, reportBody("끝냈습니다")), 400, "INVALID_INPUT");
    }

    // 평가 단계에서는 담당 조원도 보고할 수 없습니다. 권한 검사가 먼저입니다. 시작 데이터의 평가 단계 항목은 이미 보고됐지만 단계 검사가 먼저라 400입니다.
    @Test
    void evaluationPhaseBlocksReport() throws Exception {
        Team evaluationTeam = teamRepository.findByInviteCode("demo-evaluation-1").orElseThrow();
        String list = api.get("/teams/" + evaluationTeam.getId() + "/contributions", api.login("student02@example.com"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(list, "$[0].id");
        String path = "/contributions/" + id + "/report";

        ResultActions blocked = api.post(path, api.login("student02@example.com"), reportBody("끝냈습니다"));
        TestApi.expectError(blocked, 400, "INVALID_INPUT");
        blocked.andExpect(jsonPath("$.message").value("평가 단계에서는 조 운영과 과정 기록을 바꿀 수 없습니다"));
        TestApi.expectError(api.post(path, api.login("student01@example.com"), reportBody("끝냈습니다")), 403, "FORBIDDEN");
    }

    // 조장이 보고 전 항목을 취소하면 취소 시각이 저장되고, 목록에 남습니다. 다시 등록할 수 있습니다.
    @Test
    void leaderCancelsAndItStaysInList() throws Exception {
        String leader = api.login("student01@example.com");
        int id = createAssignedToStudent02();

        api.post("/contributions/" + id + "/cancel", leader, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.canceledAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")))
                .andExpect(jsonPath("$.reportedAt").isEmpty());

        api.get(contributionsPath, api.login("professor@example.com"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].canceledAt").isNotEmpty());

        int again = createAssignedToStudent02();
        api.get(contributionsPath, leader)
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(again))
                .andExpect(jsonPath("$[0].canceledAt").isEmpty());
    }

    // 조장만 취소합니다. 담당 조원, 다른 조 학생, 교수는 403이고 항목은 그대로입니다.
    @Test
    void onlyLeaderCancels() throws Exception {
        int id = createAssignedToStudent02();

        for (String email : List.of("student02@example.com", "student06@example.com", "professor@example.com")) {
            TestApi.expectError(api.post("/contributions/" + id + "/cancel", api.login(email), ""), 403, "FORBIDDEN");
        }
        api.get(contributionsPath, api.login("student01@example.com")).andExpect(jsonPath("$[0].canceledAt").isEmpty());
    }

    // 보고한 항목은 취소할 수 없고, 취소한 항목은 다시 취소하거나 보고할 수 없습니다. 처음 기록은 그대로입니다.
    @Test
    void reportAndCancelExcludeEachOther() throws Exception {
        String leader = api.login("student01@example.com");
        String member = api.login("student02@example.com");
        int reported = createAssignedToStudent02();
        api.post("/contributions/" + reported + "/report", member, reportBody("끝냈습니다")).andExpect(status().isOk());
        int canceled = createAssignedToStudent02();
        String canceledAt = JsonPath.read(api.post("/contributions/" + canceled + "/cancel", leader, "")
                .andReturn().getResponse().getContentAsString(), "$.canceledAt");

        expectBadRequest(api.post("/contributions/" + reported + "/cancel", leader, ""), "보고한 항목은 취소할 수 없습니다");
        expectBadRequest(api.post("/contributions/" + canceled + "/cancel", leader, ""), "이미 취소된 항목입니다");
        expectBadRequest(api.post("/contributions/" + canceled + "/report", member, reportBody("끝냈습니다")), "취소된 항목은 보고할 수 없습니다");

        api.get(contributionsPath, leader)
                .andExpect(jsonPath("$[0].canceledAt").value(canceledAt))
                .andExpect(jsonPath("$[0].reportMemo").isEmpty())
                .andExpect(jsonPath("$[1].reportMemo").value("끝냈습니다"))
                .andExpect(jsonPath("$[1].canceledAt").isEmpty());
    }

    // 조장이 바뀌면 새 조장이 취소합니다. 이전 조장은 일반 조원이라 403입니다.
    @Test
    void newLeaderCancelsAfterChange() throws Exception {
        int id = createAssignedToStudent02();
        api.put("/teams/" + teamId + "/leader", api.login("professor@example.com"),
                "{ \"teamMemberId\": " + members.get(2).getId() + " }").andExpect(status().isOk());

        TestApi.expectError(api.post("/contributions/" + id + "/cancel", api.login("student01@example.com"), ""), 403, "FORBIDDEN");
        api.post("/contributions/" + id + "/cancel", api.login("student03@example.com"), "").andExpect(status().isOk());
    }

    // 없는 항목은 404, 번호가 숫자가 아니면 400입니다.
    @Test
    void wrongCancelIsRejected() throws Exception {
        String leader = api.login("student01@example.com");

        TestApi.expectError(api.post("/contributions/999999/cancel", leader, ""), 404, "NOT_FOUND");
        TestApi.expectError(api.post("/contributions/abc/cancel", leader, ""), 400, "INVALID_INPUT");
    }

    // 평가 단계에서는 조장도 취소할 수 없습니다. 권한 검사가 먼저입니다.
    @Test
    void evaluationPhaseBlocksCancel() throws Exception {
        Team evaluationTeam = teamRepository.findByInviteCode("demo-evaluation-1").orElseThrow();
        String list = api.get("/teams/" + evaluationTeam.getId() + "/contributions", api.login("student01@example.com"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(list, "$[0].id");
        String path = "/contributions/" + id + "/cancel";

        expectBadRequest(api.post(path, api.login("student01@example.com"), ""), "평가 단계에서는 조 운영과 과정 기록을 바꿀 수 없습니다");
        TestApi.expectError(api.post(path, api.login("student02@example.com"), ""), 403, "FORBIDDEN");
    }

    // 400 INVALID_INPUT이고 메시지가 같은지 확인합니다.
    private void expectBadRequest(ResultActions result, String message) throws Exception {
        TestApi.expectError(result, 400, "INVALID_INPUT");
        result.andExpect(jsonPath("$.message").value(message));
    }

    // 조장이 student02에게 맡긴 항목을 등록하고 번호를 돌려줍니다.
    private int createAssignedToStudent02() throws Exception {
        String response = api.post(contributionsPath, api.login("student01@example.com"), createBody("회의 진행", members.get(1).getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    // 완료 보고 요청 body를 만듭니다.
    private String reportBody(String memo) {
        return "{ \"memo\": \"" + memo + "\" }";
    }

    // 등록 요청 body를 만듭니다.
    private String createBody(String title, Long teamMemberId) {
        return """
                { "title": "%s", "teamMemberId": %d }
                """.formatted(title, teamMemberId);
    }
}
