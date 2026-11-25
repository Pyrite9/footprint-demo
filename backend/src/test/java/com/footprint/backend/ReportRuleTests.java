package com.footprint.backend;

import com.footprint.backend.entity.Report;
import com.footprint.backend.entity.ReportPart;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.repository.ReportPartRepository;
import com.footprint.backend.repository.ReportRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 보고서와 파트 배분(X-12-1) 규칙을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 시작 데이터의 1조(student01 조장, student02~05 조원)를 씁니다. 시작 데이터에는 보고서가 없습니다.
// @Transactional: 테스트가 끝나면 데이터베이스 변경을 되돌립니다. 그래서 테스트끼리 서로 영향을 주지 않습니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportRuleTests {

    // 평가 단계 오류의 문장입니다. AssignmentPhaseChecker가 돌려줍니다.
    private static final String EVALUATION_PHASE_MESSAGE = "평가 단계에서는 조 운영과 과정 기록을 바꿀 수 없습니다";

    private final TestApi api;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ReportRepository reportRepository;
    private final ReportPartRepository reportPartRepository;

    private Long teamId;
    private String reportsPath;
    private List<TeamMember> members;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    ReportRuleTests(MockMvc mockMvc, TeamRepository teamRepository, TeamMemberRepository teamMemberRepository,
                    ReportRepository reportRepository, ReportPartRepository reportPartRepository) {
        this.api = new TestApi(mockMvc);
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.reportRepository = reportRepository;
        this.reportPartRepository = reportPartRepository;
    }

    // 시작 데이터의 1조와 조원을 찾아 둡니다. members의 순서는 student01, student02, ...입니다.
    @BeforeEach
    void findSeedTeam() {
        Team team = teamRepository.findByInviteCode("demo-team-1").orElseThrow();
        teamId = team.getId();
        reportsPath = "/teams/" + teamId + "/reports";
        members = teamMemberRepository.findByTeamIdOrderByIdAsc(teamId);
    }

    // 조장이 보고서를 만들면 제목의 앞뒤 공백을 지우고 저장하고, 만든 시각은 서버가 넣습니다. 보낸 시각은 무시합니다.
    @Test
    void leaderCreatesReport() throws Exception {
        String body = "{ \"title\": \"  중간 보고서  \", \"createdAt\": \"2000-01-01T00:00:00\" }";

        api.post(reportsPath, api.login("student01@example.com"), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("중간 보고서"))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")))
                .andExpect(jsonPath("$.createdAt").value(not(startsWith("2000"))));
    }

    // 조장만 보고서를 만듭니다. 조원, 다른 조 학생, 교수는 403입니다.
    @Test
    void onlyLeaderCreatesReport() throws Exception {
        for (String email : List.of("student02@example.com", "student06@example.com", "professor@example.com")) {
            TestApi.expectError(api.post(reportsPath, api.login(email), reportBody("중간 보고서")), 403, "FORBIDDEN");
        }
    }

    // 제목이 비었거나 255자를 넘으면 400입니다. 없는 조는 404입니다.
    @Test
    void wrongReportIsRejected() throws Exception {
        String leader = api.login("student01@example.com");

        TestApi.expectError(api.post(reportsPath, leader, reportBody("   ")), 400, "INVALID_INPUT");
        TestApi.expectError(api.post(reportsPath, leader, reportBody("가".repeat(256))), 400, "INVALID_INPUT");
        TestApi.expectError(api.post(reportsPath, leader, "{}"), 400, "INVALID_INPUT");
        TestApi.expectError(api.post("/teams/999999/reports", leader, reportBody("중간 보고서")), 404, "NOT_FOUND");
    }

    // 한 조에 보고서를 여러 개 둡니다. 조원과 그 과제를 만든 교수는 최근에 만든 것부터 봅니다. 다른 조 학생은 403입니다.
    @Test
    void membersAndProfessorSeeReportsNewestFirst() throws Exception {
        createReport("중간 보고서");
        createReport("최종 보고서");

        for (String email : List.of("student01@example.com", "student03@example.com", "professor@example.com")) {
            api.get(reportsPath, api.login(email))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].title").value("최종 보고서"))
                    .andExpect(jsonPath("$[1].title").value("중간 보고서"));
        }
        TestApi.expectError(api.get(reportsPath, api.login("student06@example.com")), 403, "FORBIDDEN");
    }

    // 조장이 파트를 등록하면 담당 조원이 펼쳐진 모양으로 돌아오고, 등록 시각은 서버가 넣습니다.
    @Test
    void leaderCreatesPart() throws Exception {
        int reportId = createReport("중간 보고서");
        String body = """
                { "title": "  3. 구현  ", "teamMemberId": %d, "createdAt": "2000-01-01T00:00:00" }
                """.formatted(members.get(1).getId());

        api.post(partsPath(reportId), api.login("student01@example.com"), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("3. 구현"))
                .andExpect(jsonPath("$.assigneeMemberId").value(members.get(1).getMember().getId()))
                .andExpect(jsonPath("$.assigneeName").value("이서연"))
                .andExpect(jsonPath("$.createdAt").value(not(startsWith("2000"))))
                .andExpect(jsonPath("$.canceledAt").isEmpty());
    }

    // 조장 자신도 담당이 될 수 있습니다.
    @Test
    void leaderCanBePartAssignee() throws Exception {
        int reportId = createReport("중간 보고서");

        api.post(partsPath(reportId), api.login("student01@example.com"), partBody("1. 개요", members.get(0).getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeName").value("김민준"));
    }

    // 조장만 파트를 등록합니다. 조원, 다른 조 학생, 교수는 403입니다.
    @Test
    void onlyLeaderCreatesPart() throws Exception {
        int reportId = createReport("중간 보고서");
        String body = partBody("1. 개요", members.get(1).getId());

        for (String email : List.of("student02@example.com", "student06@example.com", "professor@example.com")) {
            TestApi.expectError(api.post(partsPath(reportId), api.login(email), body), 403, "FORBIDDEN");
        }
    }

    // 담당은 이 조의 조원이어야 합니다. 다른 조의 조원과 없는 번호, 없는 보고서는 404입니다. 입력이 틀리면 400입니다.
    @Test
    void wrongPartIsRejected() throws Exception {
        String leader = api.login("student01@example.com");
        int reportId = createReport("중간 보고서");
        Long otherTeamId = teamRepository.findByInviteCode("demo-team-2").orElseThrow().getId();
        Long otherTeamMemberId = teamMemberRepository.findByTeamIdOrderByIdAsc(otherTeamId).get(0).getId();
        Long assigneeId = members.get(1).getId();

        TestApi.expectError(api.post(partsPath(reportId), leader, partBody("1. 개요", otherTeamMemberId)), 404, "NOT_FOUND");
        TestApi.expectError(api.post(partsPath(reportId), leader, partBody("1. 개요", 999999L)), 404, "NOT_FOUND");
        TestApi.expectError(api.post("/reports/999999/parts", leader, partBody("1. 개요", assigneeId)), 404, "NOT_FOUND");
        TestApi.expectError(api.post(partsPath(reportId), leader, partBody("   ", assigneeId)), 400, "INVALID_INPUT");
        TestApi.expectError(api.post(partsPath(reportId), leader, "{ \"title\": \"1. 개요\" }"), 400, "INVALID_INPUT");
        TestApi.expectError(api.post("/reports/abc/parts", leader, partBody("1. 개요", assigneeId)), 400, "INVALID_INPUT");
    }

    // 다른 조의 조장은 이 조의 보고서에 파트를 등록할 수 없습니다.
    @Test
    void otherTeamLeaderCannotCreatePart() throws Exception {
        int reportId = createReport("중간 보고서");
        Long otherTeamId = teamRepository.findByInviteCode("demo-team-2").orElseThrow().getId();
        TeamMember otherLeader = teamMemberRepository.findByTeamIdOrderByIdAsc(otherTeamId).stream()
                .filter(TeamMember::isLeader).findFirst().orElseThrow();

        TestApi.expectError(api.post(partsPath(reportId), api.login(otherLeader.getMember().getEmail()),
                partBody("1. 개요", members.get(1).getId())), 403, "FORBIDDEN");
    }

    // 파트 목록은 먼저 등록한 것부터입니다. 조원과 교수가 보고, 다른 조 학생은 403입니다. 없는 보고서는 404입니다.
    @Test
    void partsAreInCreatedOrder() throws Exception {
        int reportId = createReport("중간 보고서");
        createPart(reportId, "1. 개요", members.get(0).getId());
        createPart(reportId, "2. 설계", members.get(1).getId());

        for (String email : List.of("student01@example.com", "student03@example.com", "professor@example.com")) {
            api.get(partsPath(reportId), api.login(email))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].title").value("1. 개요"))
                    .andExpect(jsonPath("$[1].title").value("2. 설계"));
        }
        TestApi.expectError(api.get(partsPath(reportId), api.login("student06@example.com")), 403, "FORBIDDEN");
        TestApi.expectError(api.get("/reports/999999/parts", api.login("student01@example.com")), 404, "NOT_FOUND");
    }

    // 파트 담당이 되면 화면 안 알림이 남습니다. 조장이 자신을 담당으로 정하면 남지 않습니다.
    @Test
    void assigneeGetsNotification() throws Exception {
        int reportId = createReport("중간 보고서");
        String student02 = api.login("student02@example.com");
        String leader = api.login("student01@example.com");
        String leaderBefore = api.get("/notifications", leader).andReturn().getResponse().getContentAsString();

        createPart(reportId, "2. 설계", members.get(1).getId());
        createPart(reportId, "1. 개요", members.get(0).getId());

        api.get("/notifications", student02)
                .andExpect(jsonPath("$[0].type").value("REPORT_PART_ASSIGNED"))
                .andExpect(jsonPath("$[0].message").value(matchesPattern(".*보고서 \"중간 보고서\"의 파트 \"2. 설계\" 담당이 되었습니다\\.")))
                .andExpect(jsonPath("$[0].teamId").value(teamId));
        String leaderAfter = api.get("/notifications", leader).andReturn().getResponse().getContentAsString();
        assertThat(leaderAfter).isEqualTo(leaderBefore);
    }

    // 조장이 파트를 취소하면 취소 시각이 저장되고 목록에 남습니다. 다시 등록하면 새 파트가 뒤에 붙습니다.
    @Test
    void leaderCancelsPartAndItStaysInList() throws Exception {
        String leader = api.login("student01@example.com");
        int reportId = createReport("중간 보고서");
        int partId = createPart(reportId, "1. 개요", members.get(1).getId());

        api.post(cancelPath(partId), leader, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(partId))
                .andExpect(jsonPath("$.canceledAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));
        int again = createPart(reportId, "1. 개요", members.get(2).getId());

        api.get(partsPath(reportId), api.login("professor@example.com"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(partId))
                .andExpect(jsonPath("$[0].canceledAt").isNotEmpty())
                .andExpect(jsonPath("$[1].id").value(again))
                .andExpect(jsonPath("$[1].canceledAt").isEmpty());
    }

    // 조장만 취소합니다. 담당 조원, 다른 조 학생, 교수는 403이고 파트는 그대로입니다.
    @Test
    void onlyLeaderCancelsPart() throws Exception {
        int reportId = createReport("중간 보고서");
        int partId = createPart(reportId, "1. 개요", members.get(1).getId());

        for (String email : List.of("student02@example.com", "student06@example.com", "professor@example.com")) {
            TestApi.expectError(api.post(cancelPath(partId), api.login(email), ""), 403, "FORBIDDEN");
        }
        api.get(partsPath(reportId), api.login("student01@example.com")).andExpect(jsonPath("$[0].canceledAt").isEmpty());
    }

    // 이미 취소한 파트는 다시 취소할 수 없고 처음 취소 시각이 그대로입니다. 없는 파트는 404, 번호가 숫자가 아니면 400입니다.
    @Test
    void wrongCancelIsRejected() throws Exception {
        String leader = api.login("student01@example.com");
        int reportId = createReport("중간 보고서");
        int partId = createPart(reportId, "1. 개요", members.get(1).getId());
        String canceledAt = JsonPath.read(api.post(cancelPath(partId), leader, "")
                .andReturn().getResponse().getContentAsString(), "$.canceledAt");

        expectBadRequest(api.post(cancelPath(partId), leader, ""), "이미 취소된 파트입니다");
        api.get(partsPath(reportId), leader).andExpect(jsonPath("$[0].canceledAt").value(canceledAt));
        TestApi.expectError(api.post("/report-parts/999999/cancel", leader, ""), 404, "NOT_FOUND");
        TestApi.expectError(api.post("/report-parts/abc/cancel", leader, ""), 400, "INVALID_INPUT");
    }

    // 조장이 바뀌면 새 조장이 파트를 등록하고 취소합니다. 이전 조장은 일반 조원이라 403입니다.
    @Test
    void newLeaderManagesPartsAfterChange() throws Exception {
        int reportId = createReport("중간 보고서");
        int partId = createPart(reportId, "1. 개요", members.get(1).getId());
        api.put("/teams/" + teamId + "/leader", api.login("professor@example.com"),
                "{ \"teamMemberId\": " + members.get(2).getId() + " }").andExpect(status().isOk());

        TestApi.expectError(api.post(cancelPath(partId), api.login("student01@example.com"), ""), 403, "FORBIDDEN");
        api.post(cancelPath(partId), api.login("student03@example.com"), "").andExpect(status().isOk());
    }

    // 평가 단계에서는 보고서 등록, 파트 등록, 파트 취소를 거부합니다. 권한 검사가 먼저이고, 목록은 그대로 볼 수 있습니다.
    // 평가 단계의 조에는 API로 보고서를 만들 수 없어서 Repository로 바로 저장해 둡니다.
    @Test
    void evaluationPhaseBlocksWrites() throws Exception {
        Team evaluationTeam = teamRepository.findByInviteCode("demo-evaluation-1").orElseThrow();
        List<TeamMember> evaluationMembers = teamMemberRepository.findByTeamIdOrderByIdAsc(evaluationTeam.getId());
        Report report = reportRepository.save(new Report(evaluationTeam, "최종 보고서"));
        ReportPart part = reportPartRepository.save(new ReportPart(report, evaluationMembers.get(1), "1. 개요"));
        String leader = api.login("student01@example.com");
        String member = api.login("student02@example.com");
        String path = "/teams/" + evaluationTeam.getId() + "/reports";

        expectBadRequest(api.post(path, leader, reportBody("새 보고서")), EVALUATION_PHASE_MESSAGE);
        expectBadRequest(api.post(partsPath(report.getId()), leader, partBody("2. 설계", evaluationMembers.get(1).getId())), EVALUATION_PHASE_MESSAGE);
        expectBadRequest(api.post(cancelPath(part.getId()), leader, ""), EVALUATION_PHASE_MESSAGE);
        TestApi.expectError(api.post(path, member, reportBody("새 보고서")), 403, "FORBIDDEN");
        TestApi.expectError(api.post(cancelPath(part.getId()), member, ""), 403, "FORBIDDEN");

        api.get(path, member).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        api.get(partsPath(report.getId()), member).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    // 400 INVALID_INPUT이고 메시지가 같은지 확인합니다.
    private void expectBadRequest(ResultActions result, String message) throws Exception {
        TestApi.expectError(result, 400, "INVALID_INPUT");
        result.andExpect(jsonPath("$.message").value(message));
    }

    // 조장이 보고서를 만들고 번호를 돌려줍니다.
    private int createReport(String title) throws Exception {
        String response = api.post(reportsPath, api.login("student01@example.com"), reportBody(title))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    // 조장이 파트를 등록하고 번호를 돌려줍니다.
    private int createPart(int reportId, String title, Long teamMemberId) throws Exception {
        String response = api.post(partsPath(reportId), api.login("student01@example.com"), partBody(title, teamMemberId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    // 파트 목록·등록 주소입니다.
    private String partsPath(long reportId) {
        return "/reports/" + reportId + "/parts";
    }

    // 파트 취소 주소입니다.
    private String cancelPath(long partId) {
        return "/report-parts/" + partId + "/cancel";
    }

    // 보고서 등록 요청 body를 만듭니다.
    private String reportBody(String title) {
        return "{ \"title\": \"" + title + "\" }";
    }

    // 파트 등록 요청 body를 만듭니다.
    private String partBody(String title, Long teamMemberId) {
        return """
                { "title": "%s", "teamMemberId": %d }
                """.formatted(title, teamMemberId);
    }
}
