package com.footprint.backend;

import com.footprint.backend.entity.Member;
import com.footprint.backend.entity.MemberType;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.repository.DeliverableRepository;
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

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 과제 단계 규칙을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 시작 데이터의 평가 단계 과제(1조 student01~03)를 씁니다.
// @Transactional: 테스트가 끝나면 데이터베이스 변경을 되돌립니다. 그래서 테스트끼리 서로 영향을 주지 않습니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PhaseRuleTests {

    private static final String BLOCKED_MESSAGE = "평가 단계에서는 조 운영과 과정 기록을 바꿀 수 없습니다";

    private final TestApi api;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final DeliverableRepository deliverableRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    private Team evaluationTeam;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    PhaseRuleTests(MockMvc mockMvc, TeamRepository teamRepository, TeamMemberRepository teamMemberRepository,
                   DeliverableRepository deliverableRepository, MemberRepository memberRepository,
                   PasswordEncoder passwordEncoder) {
        this.api = new TestApi(mockMvc);
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.deliverableRepository = deliverableRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 시작 데이터의 평가 단계 조를 찾아 둡니다.
    @BeforeEach
    void findSeedTeam() {
        evaluationTeam = teamRepository.findByInviteCode("demo-evaluation-1").orElseThrow();
    }

    // 새 과제는 기록 단계로 시작합니다. 과제와 조 응답 모두 단계를 알려 줍니다.
    @Test
    void newAssignmentStartsInRecording() throws Exception {
        String professor = api.login("professor@example.com");

        int assignmentId = createAssignment(professor);

        String detail = api.get("/assignments/" + assignmentId, professor)
                .andExpect(jsonPath("$.phase").value("RECORDING"))
                .andExpect(jsonPath("$.evaluationStartedAt").isEmpty())
                .andReturn().getResponse().getContentAsString();
        int teamId = JsonPath.read(detail, "$.teams[0].id");
        api.get("/teams/" + teamId, professor).andExpect(jsonPath("$.phase").value("RECORDING"));
    }

    // 과제를 만든 교수가 평가를 시작하면 서버가 시각을 기록합니다. 되돌리거나 다시 시작할 수 없습니다.
    @Test
    void professorStartsEvaluationOnce() throws Exception {
        String professor = api.login("professor@example.com");
        int assignmentId = createAssignment(professor);
        String path = "/assignments/" + assignmentId + "/start-evaluation";

        api.post(path, professor, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("EVALUATION"))
                // 초까지만 기록합니다. 다른 기록 시각, 가짜 API와 같은 모양입니다.
                .andExpect(jsonPath("$.evaluationStartedAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));

        api.get("/assignments/" + assignmentId, professor).andExpect(jsonPath("$.phase").value("EVALUATION"));
        expectBadRequest(api.post(path, professor, ""), "이미 평가 단계입니다");
    }

    // 과제를 만든 교수만 평가를 시작합니다. 그 과제의 조장도, 다른 교수도 403이고 없는 과제는 404입니다.
    @Test
    void onlyItsProfessorStartsEvaluation() throws Exception {
        int assignmentId = createAssignment(api.login("professor@example.com"));
        String path = "/assignments/" + assignmentId + "/start-evaluation";
        memberRepository.save(new Member("other-professor@example.com", passwordEncoder.encode("pass1234"),
                "다른교수", MemberType.PROFESSOR));

        TestApi.expectError(api.post(path, api.login("student01@example.com"), ""), 403, "FORBIDDEN");
        TestApi.expectError(api.post(path, api.login("other-professor@example.com"), ""), 403, "FORBIDDEN");
        TestApi.expectError(api.post("/assignments/999999/start-evaluation", api.login("professor@example.com"), ""),
                404, "NOT_FOUND");
    }

    // 평가 단계에서는 조 운영과 과정 기록의 쓰기 요청을 모두 거부합니다.
    @Test
    void writesAreRejectedInEvaluation() throws Exception {
        String leader = api.login("student01@example.com");
        Long teamId = evaluationTeam.getId();
        TeamMember member = teamMemberRepository.findByTeamIdOrderByIdAsc(teamId).get(1);
        MockMultipartFile file = new MockMultipartFile("file", "시험.txt", "text/plain", "내용".getBytes(StandardCharsets.UTF_8));

        expectBadRequest(api.post("/teams/" + teamId + "/invite-code", leader, ""), BLOCKED_MESSAGE);
        expectBadRequest(api.post("/teams/" + teamId + "/members/" + member.getId() + "/role", leader,
                "{ \"roleName\": \"기획\" }"), BLOCKED_MESSAGE);
        expectBadRequest(api.put("/teams/" + teamId + "/members/" + member.getId() + "/role", leader,
                "{ \"roleName\": \"기획\" }"), BLOCKED_MESSAGE);
        expectBadRequest(api.put("/teams/" + teamId + "/leader", api.login("professor@example.com"),
                "{ \"teamMemberId\": " + member.getId() + " }"), BLOCKED_MESSAGE);
        expectBadRequest(api.upload("/teams/" + teamId + "/deliverables", leader, file), BLOCKED_MESSAGE);
        // 시작 산출물을 올린 student02가 지우려고 해도 평가 단계에서는 막힙니다. 파일은 지워지지 않습니다.
        Long deliverableId = deliverableRepository.findByTeamMemberTeamIdOrderByIdDesc(teamId).get(0).getId();
        expectBadRequest(api.delete("/deliverables/" + deliverableId, api.login("student02@example.com")), BLOCKED_MESSAGE);
        expectBadRequest(api.post("/teams/join", api.login("student04@example.com"),
                "{ \"inviteCode\": \"demo-evaluation-1\" }"), BLOCKED_MESSAGE);
    }

    // 이미 그 조의 조원이면 평가 단계에서도 합류 요청이 200입니다. 아무것도 바꾸지 않기 때문입니다.
    @Test
    void rejoiningOwnTeamIsOkInEvaluation() throws Exception {
        api.post("/teams/join", api.login("student02@example.com"), "{ \"inviteCode\": \"demo-evaluation-1\" }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(evaluationTeam.getId()));
    }

    // 권한 검사가 단계 검사보다 먼저입니다. 조장이 아니면 평가 단계에서도 403입니다.
    @Test
    void permissionIsCheckedBeforePhase() throws Exception {
        TestApi.expectError(api.post("/teams/" + evaluationTeam.getId() + "/invite-code",
                api.login("student02@example.com"), ""), 403, "FORBIDDEN");
        Long deliverableId = deliverableRepository.findByTeamMemberTeamIdOrderByIdDesc(evaluationTeam.getId()).get(0).getId();
        TestApi.expectError(api.delete("/deliverables/" + deliverableId, api.login("student01@example.com")), 403, "FORBIDDEN");
    }

    // 평가 단계에서도 열람은 됩니다.
    @Test
    void readsAreAllowedInEvaluation() throws Exception {
        Long teamId = evaluationTeam.getId();
        Long deliverableId = deliverableRepository.findByTeamMemberTeamIdOrderByIdDesc(teamId).get(0).getId();
        Long teamMemberId = teamMemberRepository.findByTeamIdOrderByIdAsc(teamId).get(0).getId();
        List<String> paths = List.of("/teams/" + teamId, "/teams/" + teamId + "/members",
                "/teams/" + teamId + "/members/" + teamMemberId + "/role-histories",
                "/teams/" + teamId + "/leader-histories",
                "/teams/" + teamId + "/deliverables", "/deliverables/" + deliverableId + "/download");

        for (String email : List.of("student01@example.com", "professor@example.com")) {
            String token = api.login(email);
            for (String path : paths) {
                api.get(path, token).andExpect(status().isOk());
            }
        }
        api.get("/teams/" + teamId + "/invite-code", api.login("student01@example.com")).andExpect(status().isOk());
    }

    // student01을 조장으로 새 과제를 만들고 과제 id를 돌려줍니다.
    private int createAssignment(String professorToken) throws Exception {
        String response = api.post("/assignments", professorToken, """
                        { "title": "시험 과제", "startDate": "2026-11-02", "endDate": "2026-12-11",
                          "leaderEmails": ["student01@example.com"] }
                        """)
                .andExpect(jsonPath("$.phase").value("RECORDING"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    // 400 INVALID_INPUT이고 메시지가 같은지 확인합니다. 400은 이유가 여러 가지라서 메시지까지 봅니다.
    private void expectBadRequest(ResultActions result, String message) throws Exception {
        TestApi.expectError(result, 400, "INVALID_INPUT");
        result.andExpect(jsonPath("$.message").value(message));
    }
}
