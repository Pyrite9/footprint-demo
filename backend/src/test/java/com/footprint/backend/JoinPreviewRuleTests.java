package com.footprint.backend;

import com.footprint.backend.repository.TeamMemberRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 합류 전 미리보기(X-4) 규칙을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 미리보기는 합류와 같은 검사를 같은 순서로 하고 같은 오류를 돌려줍니다. 합류는 하지 않습니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JoinPreviewRuleTests {

    private final TestApi api;
    private final TeamMemberRepository teamMemberRepository;

    @Autowired
    JoinPreviewRuleTests(MockMvc mockMvc, TeamMemberRepository teamMemberRepository) {
        this.api = new TestApi(mockMvc);
        this.teamMemberRepository = teamMemberRepository;
    }

    // 유효한 코드이면 과제 제목, 조 이름, 조장 이름만 받습니다. 미리보기만 해서는 조원이 되지 않습니다.
    @Test
    void previewShowsTeamWithoutJoining() throws Exception {
        int teamId = createAssignmentAndGetTeamId("student01@example.com");
        String code = issueInviteCode(teamId, api.login("student01@example.com"));
        String joiner = api.login("student02@example.com");

        api.get("/teams/join/" + code, joiner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentTitle").value("시험 과제"))
                .andExpect(jsonPath("$.teamName").value("1조"))
                .andExpect(jsonPath("$.leaderName").value("김민준"))
                // 조원 수, 조원 목록, 조 번호는 담지 않습니다.
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.members").doesNotExist());

        assertThat(teamMemberRepository.findByTeamIdOrderByIdAsc((long) teamId)).hasSize(1);
        TestApi.expectError(api.get("/teams/" + teamId, joiner), 403, "FORBIDDEN");
    }

    // 다시 발급하면 이전 코드는 404이고 새 코드는 200입니다. 없는 코드도 404입니다.
    @Test
    void reissuedOrUnknownCodeIsNotFound() throws Exception {
        int teamId = createAssignmentAndGetTeamId("student01@example.com");
        String leader = api.login("student01@example.com");
        String oldCode = issueInviteCode(teamId, leader);
        String newCode = issueInviteCode(teamId, leader);
        String joiner = api.login("student02@example.com");

        TestApi.expectError(api.get("/teams/join/" + oldCode, joiner), 404, "NOT_FOUND");
        TestApi.expectError(api.get("/teams/join/no-such-code", joiner), 404, "NOT_FOUND");
        api.get("/teams/join/" + newCode, joiner).andExpect(status().isOk());
    }

    // 이미 그 조의 조원이면 합류처럼 200입니다. 같은 과제의 다른 조에 있으면 합류처럼 409입니다.
    @Test
    void existingMembershipFollowsJoinRule() throws Exception {
        String student02 = api.login("student02@example.com");

        api.get("/teams/join/demo-team-1", student02)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentTitle").value("발자국 데모 과제"))
                .andExpect(jsonPath("$.leaderName").value("김민준"));
        TestApi.expectError(api.get("/teams/join/demo-team-2", student02), 409, "DUPLICATE");
    }

    // 평가 단계 과제의 조는 새로 합류할 학생에게 400입니다. 이미 그 조의 조원이면 200입니다.
    @Test
    void evaluationPhaseFollowsJoinRule() throws Exception {
        TestApi.expectError(api.get("/teams/join/demo-evaluation-1", api.login("student04@example.com")),
                400, "INVALID_INPUT");
        api.get("/teams/join/demo-evaluation-1", api.login("student01@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentTitle").value("발자국 평가 단계 과제"));
    }

    // 교수는 403, token이 없으면 401입니다.
    @Test
    void professorAndAnonymousCannotPreview() throws Exception {
        TestApi.expectError(api.get("/teams/join/demo-team-1", api.login("professor@example.com")), 403, "FORBIDDEN");
        TestApi.expectError(api.getWithoutToken("/teams/join/demo-team-1"), 401, "UNAUTHORIZED");
    }

    // 조장 한 명으로 새 과제를 만들고 그 조의 id를 돌려줍니다.
    private int createAssignmentAndGetTeamId(String leaderEmail) throws Exception {
        String token = api.login("professor@example.com");
        String body = """
                { "title": "시험 과제", "startDate": "2026-11-02", "endDate": "2026-12-11", "leaderEmails": ["%s"] }
                """.formatted(leaderEmail);
        String created = api.post("/assignments", token, body).andReturn().getResponse().getContentAsString();
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
}
