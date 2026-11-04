package com.footprint.backend;

import com.footprint.backend.entity.Evaluation;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.repository.EvaluationRepository;
import com.footprint.backend.repository.TeamMemberRepository;
import com.footprint.backend.repository.TeamRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 동료평가 제출 규칙을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 시작 데이터의 평가 단계 과제 1조(student01~03)에서 student01이 나머지 2명을 평가합니다.
// @Transactional: 테스트가 끝나면 데이터베이스 변경을 되돌립니다. 그래서 테스트끼리 서로 영향을 주지 않습니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EvaluationRuleTests {

    private final TestApi api;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final EvaluationRepository evaluationRepository;

    private String evaluationsPath;
    private TeamMember evaluator;
    private List<TeamMember> targets;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    EvaluationRuleTests(MockMvc mockMvc, TeamRepository teamRepository, TeamMemberRepository teamMemberRepository,
                        EvaluationRepository evaluationRepository) {
        this.api = new TestApi(mockMvc);
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.evaluationRepository = evaluationRepository;
    }

    // 평가 단계 조의 평가자(조장 student01)와 평가 대상 2명을 찾아 둡니다.
    @BeforeEach
    void findSeedTeam() {
        Team team = teamRepository.findByInviteCode("demo-evaluation-1").orElseThrow();
        evaluationsPath = "/teams/" + team.getId() + "/evaluations";
        List<TeamMember> members = teamMemberRepository.findByTeamIdOrderByIdAsc(team.getId());
        evaluator = members.get(0);
        targets = members.subList(1, members.size());
    }

    // 자신을 뺀 전원을 평가하면 제출됩니다. 응답은 제출 여부뿐이고 점수를 돌려주지 않습니다. 두 번째 제출은 409입니다.
    @Test
    void submitOnceThenDuplicate() throws Exception {
        String token = api.login("student01@example.com");
        String body = evaluationsBody(allTargetItems("3"));

        api.post(evaluationsPath, token, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.submitted").value(true))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty());

        api.get(evaluationsPath + "/mine", token).andExpect(jsonPath("$.submitted").value(true));
        TestApi.expectError(api.post(evaluationsPath, token, body), 409, "DUPLICATE");
    }

    // 기록 단계에서는 제출할 수 없습니다. 제출 여부를 보는 것은 됩니다.
    @Test
    void submitIsRejectedInRecording() throws Exception {
        Team recordingTeam = teamRepository.findByInviteCode("demo-team-1").orElseThrow();
        String path = "/teams/" + recordingTeam.getId() + "/evaluations";
        String token = api.login("student01@example.com");

        var result = api.post(path, token, evaluationsBody(allTargetItems("3")));

        TestApi.expectError(result, 400, "INVALID_INPUT");
        result.andExpect(jsonPath("$.message").value("동료평가는 평가 단계에서만 입력할 수 있습니다"));
        api.get(path + "/mine", token).andExpect(jsonPath("$.submitted").value(false));
    }

    // 대상이 "자신을 뺀 전원 한 번씩"이 아니면 400이고 아무것도 저장되지 않습니다.
    @Test
    void wrongTargetsAreRejected() throws Exception {
        String token = api.login("student01@example.com");
        List<String> complete = allTargetItems("3");
        Team otherTeam = teamRepository.findByInviteCode("demo-team-2").orElseThrow();
        TeamMember otherTeamMember = teamMemberRepository.findByTeamIdOrderByIdAsc(otherTeam.getId()).get(0);

        List<List<String>> wrongItemLists = List.of(
                complete.subList(1, complete.size()),
                withItem(complete, item(targets.get(0).getId(), "3")),
                withItem(complete, item(evaluator.getId(), "3")),
                withItem(complete, item(otherTeamMember.getId(), "3")));

        for (List<String> items : wrongItemLists) {
            TestApi.expectError(api.post(evaluationsPath, token, evaluationsBody(items)), 400, "INVALID_INPUT");
        }
        api.get(evaluationsPath + "/mine", token).andExpect(jsonPath("$.submitted").value(false));
    }

    // 점수는 1~5의 정수만 받습니다. 0, 6, 소수, 문자열, 빠진 값은 모두 400입니다.
    @Test
    void wrongScoresAreRejected() throws Exception {
        String token = api.login("student01@example.com");

        for (String score : List.of("0", "6", "4.5", "5.0", "\"5\"", "null")) {
            String body = evaluationsBody(allTargetItems(score));
            TestApi.expectError(api.post(evaluationsPath, token, body), 400, "INVALID_INPUT");
        }
    }

    // 의견은 2000자까지입니다.
    @Test
    void tooLongCommentIsRejected() throws Exception {
        List<String> items = new ArrayList<>(allTargetItems("3"));
        String longComment = "가".repeat(2001);
        items.set(0, items.get(0).replace("\"comment\": null", "\"comment\": \"" + longComment + "\""));

        TestApi.expectError(api.post(evaluationsPath, api.login("student01@example.com"), evaluationsBody(items)),
                400, "INVALID_INPUT");
    }

    // 교수와 다른 조 학생은 그 조에서 평가를 제출하거나 제출 여부를 볼 수 없습니다.
    @Test
    void outsiderIsForbidden() throws Exception {
        String body = evaluationsBody(allTargetItems("3"));

        for (String email : List.of("professor@example.com", "student06@example.com")) {
            String token = api.login(email);
            TestApi.expectError(api.post(evaluationsPath, token, body), 403, "FORBIDDEN");
            TestApi.expectError(api.get(evaluationsPath + "/mine", token), 403, "FORBIDDEN");
        }
    }

    // 조에 자신만 있으면 평가할 대상이 없어서 400입니다.
    @Test
    void aloneInTeamIsRejected() throws Exception {
        String professor = api.login("professor@example.com");
        String created = api.post("/assignments", professor, """
                        { "title": "시험 과제", "startDate": "2026-11-02", "endDate": "2026-12-11",
                          "leaderEmails": ["student01@example.com"] }
                        """)
                .andReturn().getResponse().getContentAsString();
        int assignmentId = JsonPath.read(created, "$.id");
        String detail = api.get("/assignments/" + assignmentId, professor).andReturn().getResponse().getContentAsString();
        int teamId = JsonPath.read(detail, "$.teams[0].id");
        api.post("/assignments/" + assignmentId + "/start-evaluation", professor, "").andExpect(status().isOk());

        TestApi.expectError(api.post("/teams/" + teamId + "/evaluations", api.login("student01@example.com"),
                evaluationsBody(allTargetItems("3"))), 400, "INVALID_INPUT");
    }

    // 같은 평가자가 같은 대상을 두 번 평가하지 못하도록 데이터베이스 unique 제약도 있습니다.
    // Service의 검사를 지나치는 동시 요청을 막는 마지막 장치라서 Repository로 바로 저장해 확인합니다.
    @Test
    void databaseRejectsSameEvaluatorAndTarget() {
        TeamMember target = targets.get(0);
        evaluationRepository.saveAndFlush(newEvaluation(target));

        assertThrows(DataIntegrityViolationException.class,
                () -> evaluationRepository.saveAndFlush(newEvaluation(target)));
    }

    private Evaluation newEvaluation(TeamMember target) {
        return new Evaluation(evaluator, target, 3, 3, 3, 3, 3, 3, null, LocalDateTime.now());
    }

    // 평가 대상 2명의 평가 JSON 목록을 만듭니다. 모든 항목에 같은 점수를 넣습니다.
    private List<String> allTargetItems(String score) {
        List<String> items = new ArrayList<>();
        for (TeamMember target : targets) {
            items.add(item(target.getId(), score));
        }
        return items;
    }

    private List<String> withItem(List<String> items, String added) {
        List<String> result = new ArrayList<>(items);
        result.add(added);
        return result;
    }

    private String item(Long targetTeamMemberId, String score) {
        return """
                { "targetTeamMemberId": %d, "goalSharing": %s, "roleFulfillment": %s, "encouragement": %s,
                  "onlineCommunication": %s, "offlineCommunication": %s, "progressCheck": %s, "comment": null }
                """.formatted(targetTeamMemberId, score, score, score, score, score, score);
    }

    private String evaluationsBody(List<String> items) {
        return "{ \"evaluations\": [" + String.join(",", items) + "] }";
    }
}
