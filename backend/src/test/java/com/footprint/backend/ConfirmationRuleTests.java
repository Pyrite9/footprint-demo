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

// 완료확인 규칙을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 시작 데이터의 1조(student01 조장, student02~05 조원)를 씁니다. student01이 올린 산출물 "기획서.txt"가 있습니다.
// @Transactional: 테스트가 끝나면 데이터베이스 변경을 되돌립니다. 그래서 테스트끼리 서로 영향을 주지 않습니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConfirmationRuleTests {

    private static final String TIME_PATTERN = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}";

    private final TestApi api;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    private Long teamId;
    private List<TeamMember> members;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    ConfirmationRuleTests(MockMvc mockMvc, TeamRepository teamRepository, TeamMemberRepository teamMemberRepository) {
        this.api = new TestApi(mockMvc);
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    // 시작 데이터의 1조와 조원을 찾아 둡니다. members의 순서는 student01, student02, ...입니다.
    @BeforeEach
    void findSeedTeam() {
        Team team = teamRepository.findByInviteCode("demo-team-1").orElseThrow();
        teamId = team.getId();
        members = teamMemberRepository.findByTeamIdOrderByIdAsc(teamId);
    }

    // 조원이 산출물을 확인하면 확인 시각은 서버가 넣고, 조원과 교수가 이력을 봅니다.
    @Test
    void memberConfirmsDeliverable() throws Exception {
        int deliverableId = seedDeliverableId();
        String path = "/deliverables/" + deliverableId + "/confirmations";

        api.post(path, api.login("student02@example.com"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliverableId").value(deliverableId))
                .andExpect(jsonPath("$.contributionId").isEmpty())
                .andExpect(jsonPath("$.confirmerMemberId").value(members.get(1).getMember().getId()))
                .andExpect(jsonPath("$.confirmerName").value("이서연"))
                .andExpect(jsonPath("$.confirmedAt").value(matchesPattern(TIME_PATTERN)))
                .andExpect(jsonPath("$.canceledAt").isEmpty());

        for (String email : List.of("student03@example.com", "professor@example.com")) {
            api.get(path, api.login(email))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].confirmerName").value("이서연"));
        }
    }

    // 조원이 보고된 기여 항목을 확인합니다. 조장도 다른 조원과 똑같이 확인합니다.
    @Test
    void leaderConfirmsReportedContribution() throws Exception {
        int contributionId = reportedContributionId();

        api.post("/contributions/" + contributionId + "/confirmations", api.login("student01@example.com"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contributionId").value(contributionId))
                .andExpect(jsonPath("$.deliverableId").isEmpty())
                .andExpect(jsonPath("$.confirmerName").value("김민준"));
    }

    // 올린 조원과 담당 조원 본인은 자기 기록을 확인할 수 없습니다.
    @Test
    void ownerCannotConfirm() throws Exception {
        TestApi.expectError(api.post("/deliverables/" + seedDeliverableId() + "/confirmations", api.login("student01@example.com"), ""), 403, "FORBIDDEN");
        TestApi.expectError(api.post("/contributions/" + reportedContributionId() + "/confirmations", api.login("student02@example.com"), ""), 403, "FORBIDDEN");
    }

    // 교수는 이력만 봅니다. 교수와 다른 조 학생은 확인할 수 없습니다.
    @Test
    void professorAndOutsiderCannotConfirm() throws Exception {
        int deliverableId = seedDeliverableId();
        int contributionId = reportedContributionId();

        for (String email : List.of("professor@example.com", "student06@example.com")) {
            TestApi.expectError(api.post("/deliverables/" + deliverableId + "/confirmations", api.login(email), ""), 403, "FORBIDDEN");
            TestApi.expectError(api.post("/contributions/" + contributionId + "/confirmations", api.login(email), ""), 403, "FORBIDDEN");
        }
    }

    // 한 기록에 여러 조원이 확인할 수 있으나, 한 조원의 유효한 확인은 하나입니다. 이력은 최근 것부터 나옵니다.
    @Test
    void severalMembersConfirmButEachOnlyOnce() throws Exception {
        String path = "/deliverables/" + seedDeliverableId() + "/confirmations";
        String member = api.login("student02@example.com");

        api.post(path, member, "").andExpect(status().isOk());
        api.post(path, api.login("student03@example.com"), "").andExpect(status().isOk());
        TestApi.expectError(api.post(path, member, ""), 409, "DUPLICATE");

        api.get(path, member)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].confirmerName").value("박도윤"))
                .andExpect(jsonPath("$[1].confirmerName").value("이서연"));
    }

    // 삭제된 산출물은 새로 확인할 수 없습니다. 삭제 전의 확인은 이력에 남습니다.
    @Test
    void deletedDeliverableKeepsHistoryButRejectsConfirm() throws Exception {
        String uploader = api.login("student02@example.com");
        // 시작 산출물을 지우면 MinIO 파일이 되돌아오지 않아서 새로 올린 파일을 지웁니다.
        MockMultipartFile file = new MockMultipartFile("file", "지울 파일.txt", "text/plain", "내용".getBytes(StandardCharsets.UTF_8));
        String uploaded = api.upload("/teams/" + teamId + "/deliverables", uploader, file)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        int deliverableId = JsonPath.read(uploaded, "$.id");
        String path = "/deliverables/" + deliverableId + "/confirmations";
        api.post(path, api.login("student03@example.com"), "").andExpect(status().isOk());

        api.delete("/deliverables/" + deliverableId, uploader).andExpect(status().isOk());

        expectBadRequest(api.post(path, api.login("student04@example.com"), ""), "삭제된 산출물은 확인할 수 없습니다");
        api.get(path, api.login("professor@example.com"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].confirmerName").value("박도윤"));
    }

    // 보고 전 항목과 취소된 항목은 확인할 수 없습니다. 이력은 빈 목록입니다.
    @Test
    void onlyReportedContributionCanBeConfirmed() throws Exception {
        String leader = api.login("student01@example.com");
        int notReported = createContribution();
        int canceled = createContribution();
        api.post("/contributions/" + canceled + "/cancel", leader, "").andExpect(status().isOk());

        expectBadRequest(api.post("/contributions/" + notReported + "/confirmations", leader, ""), "보고 전 항목은 확인할 수 없습니다");
        expectBadRequest(api.post("/contributions/" + canceled + "/confirmations", leader, ""), "취소된 항목은 확인할 수 없습니다");
        api.get("/contributions/" + notReported + "/confirmations", leader)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // 다른 조 학생은 이력을 볼 수 없습니다. 없는 기록은 404, 번호가 숫자가 아니면 400입니다.
    @Test
    void historyAccessIsChecked() throws Exception {
        String outsider = api.login("student06@example.com");
        String member = api.login("student02@example.com");

        TestApi.expectError(api.get("/deliverables/" + seedDeliverableId() + "/confirmations", outsider), 403, "FORBIDDEN");
        TestApi.expectError(api.get("/contributions/" + reportedContributionId() + "/confirmations", outsider), 403, "FORBIDDEN");
        for (String kind : List.of("deliverables", "contributions")) {
            TestApi.expectError(api.get("/" + kind + "/999999/confirmations", member), 404, "NOT_FOUND");
            TestApi.expectError(api.post("/" + kind + "/999999/confirmations", member, ""), 404, "NOT_FOUND");
            TestApi.expectError(api.get("/" + kind + "/abc/confirmations", member), 400, "INVALID_INPUT");
            TestApi.expectError(api.post("/" + kind + "/abc/confirmations", member, ""), 400, "INVALID_INPUT");
        }
    }

    // 평가 단계에서는 새로 확인할 수 없고 이력은 그대로 봅니다. 권한 검사가 먼저입니다.
    @Test
    void evaluationPhaseBlocksConfirmButKeepsHistory() throws Exception {
        Team evaluationTeam = teamRepository.findByInviteCode("demo-evaluation-1").orElseThrow();
        String phaseMessage = "평가 단계에서는 조 운영과 과정 기록을 바꿀 수 없습니다";
        String professor = api.login("professor@example.com");
        int deliverableId = firstId("/teams/" + evaluationTeam.getId() + "/deliverables", professor);
        int contributionId = firstId("/teams/" + evaluationTeam.getId() + "/contributions", professor);

        // 시작 데이터: 최종 보고서는 student02가 올리고 student01이 확인했습니다. 자료 취합은 student02가 맡고 student03이 확인했습니다.
        expectBadRequest(api.post("/deliverables/" + deliverableId + "/confirmations", api.login("student03@example.com"), ""), phaseMessage);
        expectBadRequest(api.post("/contributions/" + contributionId + "/confirmations", api.login("student01@example.com"), ""), phaseMessage);
        TestApi.expectError(api.post("/deliverables/" + deliverableId + "/confirmations", api.login("student02@example.com"), ""), 403, "FORBIDDEN");

        api.get("/deliverables/" + deliverableId + "/confirmations", professor)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].confirmerName").value("김민준"));
        api.get("/contributions/" + contributionId + "/confirmations", professor)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].confirmerName").value("박도윤"));
    }

    // 400 INVALID_INPUT이고 메시지가 같은지 확인합니다.
    private void expectBadRequest(ResultActions result, String message) throws Exception {
        TestApi.expectError(result, 400, "INVALID_INPUT");
        result.andExpect(jsonPath("$.message").value(message));
    }

    // 1조의 시작 산출물 중 student01이 올린 "기획서.txt"의 번호입니다.
    private int seedDeliverableId() throws Exception {
        String list = api.get("/teams/" + teamId + "/deliverables", api.login("student01@example.com"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Integer> ids = JsonPath.read(list, "$[?(@.fileName == '기획서.txt')].id");
        return ids.get(0);
    }

    // student02가 맡은 항목을 새로 등록하고 번호를 돌려줍니다. 보고 전 상태입니다.
    private int createContribution() throws Exception {
        String body = "{ \"title\": \"회의 진행\", \"teamMemberId\": " + members.get(1).getId() + " }";
        String created = api.post("/teams/" + teamId + "/contributions", api.login("student01@example.com"), body)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.id");
    }

    // student02가 맡아 완료 보고까지 한 항목의 번호를 돌려줍니다.
    private int reportedContributionId() throws Exception {
        int id = createContribution();
        api.post("/contributions/" + id + "/report", api.login("student02@example.com"), "{ \"memo\": \"끝냈습니다\" }")
                .andExpect(status().isOk());
        return id;
    }

    // 목록 맨 앞 줄의 번호를 돌려줍니다.
    private int firstId(String path, String token) throws Exception {
        return JsonPath.read(api.get(path, token).andReturn().getResponse().getContentAsString(), "$[0].id");
    }
}
