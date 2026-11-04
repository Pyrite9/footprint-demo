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

    // 등록 요청 body를 만듭니다.
    private String createBody(String title, Long teamMemberId) {
        return """
                { "title": "%s", "teamMemberId": %d }
                """.formatted(title, teamMemberId);
    }
}
