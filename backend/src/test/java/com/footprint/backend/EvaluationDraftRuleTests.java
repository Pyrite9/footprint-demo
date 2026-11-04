package com.footprint.backend;

import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.repository.TeamMemberRepository;
import com.footprint.backend.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 동료평가 임시 저장 규칙을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 시작 데이터의 평가 단계 과제 1조(student01~03)에서 student01이 나머지 2명의 평가를 임시 저장합니다.
// @Transactional: 테스트가 끝나면 데이터베이스 변경을 되돌립니다. 그래서 테스트끼리 서로 영향을 주지 않습니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EvaluationDraftRuleTests {

    private final TestApi api;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    private String evaluationsPath;
    private String draftPath;
    private TeamMember evaluator;
    private TeamMember firstTarget;
    private TeamMember secondTarget;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    EvaluationDraftRuleTests(MockMvc mockMvc, TeamRepository teamRepository, TeamMemberRepository teamMemberRepository) {
        this.api = new TestApi(mockMvc);
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    // 평가 단계 조의 평가자(조장 student01)와 평가 대상 2명을 찾아 둡니다.
    @BeforeEach
    void findSeedTeam() {
        Team team = teamRepository.findByInviteCode("demo-evaluation-1").orElseThrow();
        evaluationsPath = "/teams/" + team.getId() + "/evaluations";
        draftPath = evaluationsPath + "/draft";
        List<TeamMember> members = teamMemberRepository.findByTeamIdOrderByIdAsc(team.getId());
        evaluator = members.get(0);
        firstTarget = members.get(1);
        secondTarget = members.get(2);
    }

    // 점수 하나만 채워도 저장되고, 다시 열면 그대로 나옵니다. 고르지 않은 점수는 null입니다. 제출 여부는 그대로 미제출입니다.
    @Test
    void partialDraftIsSavedAndLoaded() throws Exception {
        String token = api.login("student01@example.com");
        api.get(draftPath, token).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        api.put(draftPath, token, draftBody(draftItem(firstTarget.getId(), "4", "\"  메모  \"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        api.get(draftPath, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].targetTeamMemberId").value(firstTarget.getId()))
                .andExpect(jsonPath("$[0].goalSharing").value(4))
                .andExpect(jsonPath("$[0].roleFulfillment").isEmpty())
                .andExpect(jsonPath("$[0].comment").value("메모"))
                .andExpect(jsonPath("$[0].savedAt").isNotEmpty());
        api.get(evaluationsPath + "/mine", token)
                .andExpect(jsonPath("$.submitted").value(false))
                .andExpect(jsonPath("$.submittedAt").isEmpty());
    }

    // 다시 저장하면 마지막 내용으로 바뀝니다. 보내지 않은 조원의 임시 저장은 사라집니다. 빈 목록을 보내면 모두 지워집니다.
    @Test
    void savingAgainReplacesEverything() throws Exception {
        String token = api.login("student01@example.com");
        api.put(draftPath, token, draftBody(draftItem(firstTarget.getId(), "1", "null"), draftItem(secondTarget.getId(), "2", "null")))
                .andExpect(status().isOk());

        api.put(draftPath, token, draftBody(draftItem(secondTarget.getId(), "5", "null")))
                .andExpect(status().isOk());

        api.get(draftPath, token)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].targetTeamMemberId").value(secondTarget.getId()))
                .andExpect(jsonPath("$[0].goalSharing").value(5));

        api.put(draftPath, token, draftBody()).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        api.get(draftPath, token).andExpect(jsonPath("$", hasSize(0)));
    }

    // 저장 시각은 서버가 정합니다. 요청에 시각을 넣어도 쓰지 않습니다.
    @Test
    void savedAtFromClientIsIgnored() throws Exception {
        String token = api.login("student01@example.com");
        String body = """
                { "evaluations": [ { "targetTeamMemberId": %d, "goalSharing": 3, "savedAt": "2000-01-01T00:00:00" } ] }
                """.formatted(firstTarget.getId());

        api.put(draftPath, token, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].savedAt", not(startsWith("2000"))));
    }

    // 임시 저장은 본인만 봅니다. 같은 조의 다른 조원에게는 빈 목록이고, 교수와 다른 조 학생은 403입니다.
    @Test
    void onlyEvaluatorSeesDraft() throws Exception {
        String body = draftBody(draftItem(firstTarget.getId(), "4", "null"));
        api.put(draftPath, api.login("student01@example.com"), body).andExpect(status().isOk());

        api.get(draftPath, api.login("student02@example.com")).andExpect(jsonPath("$", hasSize(0)));
        for (String email : List.of("professor@example.com", "student06@example.com")) {
            String token = api.login(email);
            TestApi.expectError(api.get(draftPath, token), 403, "FORBIDDEN");
            TestApi.expectError(api.put(draftPath, token, body), 403, "FORBIDDEN");
        }
    }

    // 기록 단계에서는 저장할 수 없습니다. 불러오면 빈 목록입니다.
    @Test
    void savingIsRejectedInRecording() throws Exception {
        Team recordingTeam = teamRepository.findByInviteCode("demo-team-1").orElseThrow();
        List<TeamMember> members = teamMemberRepository.findByTeamIdOrderByIdAsc(recordingTeam.getId());
        String path = "/teams/" + recordingTeam.getId() + "/evaluations/draft";
        String token = api.login("student01@example.com");

        api.put(path, token, draftBody(draftItem(members.get(1).getId(), "3", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("동료평가는 평가 단계에서만 입력할 수 있습니다"));
        api.get(path, token).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    // 자기 자신, 다른 조 조원, 같은 조원 두 번은 400이고 이전 임시 저장은 그대로입니다.
    @Test
    void wrongTargetsAreRejected() throws Exception {
        String token = api.login("student01@example.com");
        Team otherTeam = teamRepository.findByInviteCode("demo-team-2").orElseThrow();
        TeamMember otherTeamMember = teamMemberRepository.findByTeamIdOrderByIdAsc(otherTeam.getId()).get(0);
        api.put(draftPath, token, draftBody(draftItem(firstTarget.getId(), "2", "null"))).andExpect(status().isOk());

        List<String> wrongBodies = List.of(
                draftBody(draftItem(evaluator.getId(), "3", "null")),
                draftBody(draftItem(otherTeamMember.getId(), "3", "null")),
                draftBody(draftItem(secondTarget.getId(), "3", "null"), draftItem(secondTarget.getId(), "3", "null")));
        for (String body : wrongBodies) {
            TestApi.expectError(api.put(draftPath, token, body), 400, "INVALID_INPUT");
        }

        api.get(draftPath, token)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].goalSharing").value(2));
    }

    // 값이 있는 점수는 1~5의 정수여야 합니다. 의견은 2000자까지입니다. evaluations가 없거나 원소가 null이면 400입니다.
    @Test
    void wrongValuesAreRejected() throws Exception {
        String token = api.login("student01@example.com");

        for (String score : List.of("0", "6", "4.5", "\"5\"")) {
            String body = draftBody(draftItem(firstTarget.getId(), score, "null"));
            TestApi.expectError(api.put(draftPath, token, body), 400, "INVALID_INPUT");
        }
        String tooLong = "\"" + "가".repeat(2001) + "\"";
        TestApi.expectError(api.put(draftPath, token, draftBody(draftItem(firstTarget.getId(), "3", tooLong))), 400, "INVALID_INPUT");
        for (String body : List.of("{ }", "{ \"evaluations\": [null] }", "{ \"evaluations\": [ { \"goalSharing\": 3 } ] }")) {
            TestApi.expectError(api.put(draftPath, token, body), 400, "INVALID_INPUT");
        }
    }

    // 제출하면 내 임시 저장만 지워집니다. 그 뒤에 저장하면 409입니다. 실패한 제출은 임시 저장을 지우지 않습니다.
    @Test
    void submitRemovesOnlyMyDraft() throws Exception {
        String token = api.login("student01@example.com");
        String otherToken = api.login("student02@example.com");
        String body = draftBody(draftItem(firstTarget.getId(), "4", "null"));
        api.put(draftPath, token, body).andExpect(status().isOk());
        api.put(draftPath, otherToken, draftBody(draftItem(secondTarget.getId(), "4", "null"))).andExpect(status().isOk());

        String oneTargetOnly = "{ \"evaluations\": [" + submitItem(firstTarget.getId()) + "] }";
        TestApi.expectError(api.post(evaluationsPath, token, oneTargetOnly), 400, "INVALID_INPUT");
        api.get(draftPath, token).andExpect(jsonPath("$", hasSize(1)));

        String allTargets = "{ \"evaluations\": [" + submitItem(firstTarget.getId()) + "," + submitItem(secondTarget.getId()) + "] }";
        api.post(evaluationsPath, token, allTargets).andExpect(status().isOk());

        api.get(draftPath, token).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        api.get(draftPath, otherToken).andExpect(jsonPath("$", hasSize(1)));
        TestApi.expectError(api.put(draftPath, token, body), 409, "DUPLICATE");
    }

    // 임시 저장 요청 body를 만듭니다.
    private String draftBody(String... items) {
        return "{ \"evaluations\": [" + String.join(",", items) + "] }";
    }

    // 점수 하나와 의견만 채운 임시 저장 하나를 만듭니다. 나머지 점수는 일부러 보내지 않습니다.
    private String draftItem(Long targetTeamMemberId, String goalSharing, String comment) {
        return """
                { "targetTeamMemberId": %d, "goalSharing": %s, "comment": %s }
                """.formatted(targetTeamMemberId, goalSharing, comment);
    }

    // 6개 점수를 모두 채운 제출용 평가 하나를 만듭니다.
    private String submitItem(Long targetTeamMemberId) {
        return """
                { "targetTeamMemberId": %d, "goalSharing": 3, "roleFulfillment": 3, "encouragement": 3,
                  "onlineCommunication": 3, "offlineCommunication": 3, "progressCheck": 3, "comment": null }
                """.formatted(targetTeamMemberId);
    }
}
