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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 화면 안 알림 규칙을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 시작 데이터의 알림: 김민준(student01)은 평가 단계 과제의 완료 보고와 평가 시작, 이서연(student02)은 평가 시작,
// 박도윤(student03)은 1조 담당 지정과 평가 단계 과제의 완료 보고, 평가 시작을 받았습니다. 교수와 2조는 알림이 없습니다.
// @Transactional: 테스트가 끝나면 데이터베이스 변경을 되돌립니다. 그래서 테스트끼리 서로 영향을 주지 않습니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationRuleTests {

    private static final String DATE_TIME_PATTERN = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}";

    private final TestApi api;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    private Team team1;
    private List<TeamMember> members;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    NotificationRuleTests(MockMvc mockMvc, TeamRepository teamRepository, TeamMemberRepository teamMemberRepository) {
        this.api = new TestApi(mockMvc);
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    // 시작 데이터의 1조와 조원을 찾아 둡니다. members의 순서는 student01, student02, ...입니다.
    @BeforeEach
    void findSeedTeam() {
        team1 = teamRepository.findByInviteCode("demo-team-1").orElseThrow();
        members = teamMemberRepository.findByTeamIdOrderByIdAsc(team1.getId());
    }

    // 시작 데이터의 알림은 받은 회원 본인에게만 최근 것부터 보입니다. 알림이 없으면 빈 목록입니다.
    @Test
    void seedNotificationsAreOwnAndNewestFirst() throws Exception {
        Long evaluationTeamId = teamRepository.findByInviteCode("demo-evaluation-1").orElseThrow().getId();

        api.get("/notifications", api.login("student01@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type").value("EVALUATION_STARTED"))
                .andExpect(jsonPath("$[0].teamId").value(evaluationTeamId))
                .andExpect(jsonPath("$[0].message").value("[발자국 평가 단계 과제 1조] 평가가 시작되었습니다. 동료평가를 입력할 수 있습니다."))
                .andExpect(jsonPath("$[0].createdAt").value(matchesPattern(DATE_TIME_PATTERN)))
                .andExpect(jsonPath("$[0].readAt").isEmpty())
                .andExpect(jsonPath("$[1].type").value("CONTRIBUTION_REPORTED"));
        api.get("/notifications", api.login("student02@example.com")).andExpect(jsonPath("$", hasSize(1)));
        api.get("/notifications", api.login("student06@example.com")).andExpect(jsonPath("$", hasSize(0)));
        api.get("/notifications", api.login("professor@example.com")).andExpect(jsonPath("$", hasSize(0)));
    }

    // token이 없으면 목록과 읽음 표시 모두 401입니다.
    @Test
    void loginIsRequired() throws Exception {
        TestApi.expectError(api.getWithoutToken("/notifications"), 401, "UNAUTHORIZED");
    }

    // 조장이 기여 항목을 등록하면 담당 조원 한 명에게만 알림이 생깁니다. 다른 조원과 교수는 받지 않습니다.
    @Test
    void assigningNotifiesOnlyAssignee() throws Exception {
        api.post(contributionsPath(), api.login("student01@example.com"), createBody("발표 자료 정리", members.get(1).getId()))
                .andExpect(status().isOk());

        api.get("/notifications", api.login("student02@example.com"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type").value("CONTRIBUTION_ASSIGNED"))
                .andExpect(jsonPath("$[0].teamId").value(team1.getId()))
                .andExpect(jsonPath("$[0].message").value("[발자국 데모 과제 1조] 기여 항목 \"발표 자료 정리\"의 담당이 되었습니다."))
                .andExpect(jsonPath("$[0].readAt").isEmpty());
        api.get("/notifications", api.login("student01@example.com"))
                .andExpect(jsonPath("$[?(@.type == 'CONTRIBUTION_ASSIGNED')]", hasSize(0)));
        api.get("/notifications", api.login("student04@example.com")).andExpect(jsonPath("$", hasSize(0)));
        api.get("/notifications", api.login("professor@example.com")).andExpect(jsonPath("$", hasSize(0)));
    }

    // 조장이 자신을 담당으로 정하면 스스로 한 일이라 알림을 만들지 않습니다.
    @Test
    void leaderAssigningSelfGetsNoNotification() throws Exception {
        String leader = api.login("student01@example.com");
        api.post(contributionsPath(), leader, createBody("자료 취합", members.get(0).getId())).andExpect(status().isOk());

        api.get("/notifications", leader)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.type == 'CONTRIBUTION_ASSIGNED')]", hasSize(0)));
    }

    // 담당 조원이 완료 보고하면 같은 조에서 보고한 조원을 뺀 전원(조장 포함)이 받습니다. 보고 메모는 담지 않습니다.
    @Test
    void reportNotifiesOtherTeamMembers() throws Exception {
        String reporter = api.login("student03@example.com");
        String contributions = api.get(contributionsPath(), reporter).andReturn().getResponse().getContentAsString();
        int contributionId = JsonPath.read(contributions, "$[0].id");
        String memo = "회의록을 정리해 조 공유 폴더에 올렸습니다.";

        api.post("/contributions/" + contributionId + "/report", reporter, "{ \"memo\": \"" + memo + "\" }")
                .andExpect(status().isOk());

        for (String email : List.of("student01@example.com", "student02@example.com", "student04@example.com", "student05@example.com")) {
            api.get("/notifications", api.login(email))
                    .andExpect(jsonPath("$[0].type").value("CONTRIBUTION_REPORTED"))
                    .andExpect(jsonPath("$[0].teamId").value(team1.getId()))
                    .andExpect(jsonPath("$[0].message").value("[발자국 데모 과제 1조] 박도윤 님이 기여 항목 \"회의록 정리\" 완료 보고를 올렸습니다."))
                    .andExpect(jsonPath("$[0].message").value(not(containsString(memo))));
        }
        api.get("/notifications", reporter)
                .andExpect(jsonPath("$[?(@.type == 'CONTRIBUTION_REPORTED' && @.teamId == " + team1.getId() + ")]", hasSize(0)));
        api.get("/notifications", api.login("student06@example.com")).andExpect(jsonPath("$", hasSize(0)));
        api.get("/notifications", api.login("professor@example.com")).andExpect(jsonPath("$", hasSize(0)));
    }

    // 평가를 시작하면 그 과제의 모든 조의 조원이 자기 조 번호로 받습니다. 시작한 교수는 받지 않습니다.
    @Test
    void startingEvaluationNotifiesEveryMemberOfAssignment() throws Exception {
        String professor = api.login("professor@example.com");
        Long team2Id = teamRepository.findByInviteCode("demo-team-2").orElseThrow().getId();

        api.post("/assignments/" + team1.getAssignment().getId() + "/start-evaluation", professor, "")
                .andExpect(status().isOk());

        for (String email : List.of("student04@example.com", "student05@example.com")) {
            api.get("/notifications", api.login(email))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].type").value("EVALUATION_STARTED"))
                    .andExpect(jsonPath("$[0].teamId").value(team1.getId()))
                    .andExpect(jsonPath("$[0].message").value("[발자국 데모 과제 1조] 평가가 시작되었습니다. 동료평가를 입력할 수 있습니다."));
        }
        for (String email : List.of("student06@example.com", "student10@example.com")) {
            api.get("/notifications", api.login(email))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].teamId").value(team2Id));
        }
        api.get("/notifications", professor).andExpect(jsonPath("$", hasSize(0)));
    }

    // 동료평가를 제출해도 알림이 생기지 않습니다. 알림에는 점수와 의견 칸이 없습니다.
    @Test
    void evaluationIsNeverInNotifications() throws Exception {
        Team evaluationTeam = teamRepository.findByInviteCode("demo-evaluation-1").orElseThrow();
        List<TeamMember> evaluationMembers = teamMemberRepository.findByTeamIdOrderByIdAsc(evaluationTeam.getId());
        String comment = "자료 조사를 꼼꼼하게 함";
        StringBuilder items = new StringBuilder();
        for (TeamMember target : evaluationMembers.subList(1, evaluationMembers.size())) {
            if (!items.isEmpty()) {
                items.append(",");
            }
            items.append("""
                    { "targetTeamMemberId": %d, "goalSharing": 5, "roleFulfillment": 5, "encouragement": 5,
                      "onlineCommunication": 5, "offlineCommunication": 5, "progressCheck": 5, "comment": "%s" }
                    """.formatted(target.getId(), comment));
        }

        api.post("/teams/" + evaluationTeam.getId() + "/evaluations", api.login("student01@example.com"),
                        "{ \"evaluations\": [" + items + "] }")
                .andExpect(status().isOk());

        for (String email : List.of("student01@example.com", "student02@example.com", "student03@example.com", "professor@example.com")) {
            api.get("/notifications", api.login(email))
                    .andExpect(jsonPath("$[*].message", everyItem(not(containsString(comment)))))
                    .andExpect(jsonPath("$[*].goalSharing", hasSize(0)))
                    .andExpect(jsonPath("$[*].comment", hasSize(0)));
        }
        api.get("/notifications", api.login("student02@example.com")).andExpect(jsonPath("$", hasSize(1)));
    }

    // 받은 회원 본인이 읽음으로 표시하면 서버가 시각을 넣습니다. 다시 표시해도 처음 시각이 그대로입니다.
    @Test
    void recipientMarksRead() throws Exception {
        String student02 = api.login("student02@example.com");
        int notificationId = firstNotificationId(student02);
        String path = "/notifications/" + notificationId + "/read";

        String first = api.post(path, student02, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificationId))
                .andExpect(jsonPath("$.readAt").value(matchesPattern(DATE_TIME_PATTERN)))
                .andReturn().getResponse().getContentAsString();
        String readAt = JsonPath.read(first, "$.readAt");

        api.post(path, student02, "").andExpect(status().isOk()).andExpect(jsonPath("$.readAt").value(readAt));
        api.get("/notifications", student02).andExpect(jsonPath("$[0].readAt").value(readAt));
    }

    // 다른 회원의 알림은 같은 조원이나 교수도 읽음으로 표시할 수 없습니다. 없는 알림은 404, 숫자가 아닌 번호는 400입니다.
    @Test
    void othersCannotMarkRead() throws Exception {
        int notificationId = firstNotificationId(api.login("student02@example.com"));
        String path = "/notifications/" + notificationId + "/read";

        for (String email : List.of("student01@example.com", "student03@example.com", "professor@example.com")) {
            TestApi.expectError(api.post(path, api.login(email), ""), 403, "FORBIDDEN");
        }
        api.get("/notifications", api.login("student02@example.com")).andExpect(jsonPath("$[0].readAt").isEmpty());

        String student02 = api.login("student02@example.com");
        TestApi.expectError(api.post("/notifications/999999/read", student02, ""), 404, "NOT_FOUND");
        TestApi.expectError(api.post("/notifications/abc/read", student02, ""), 400, "INVALID_INPUT");
    }

    // 기여 항목 등록이 실패하면(권한 없음) 알림도 생기지 않습니다.
    @Test
    void failedRequestLeavesNoNotification() throws Exception {
        TestApi.expectError(api.post(contributionsPath(), api.login("student03@example.com"), createBody("회의 진행", members.get(1).getId())),
                403, "FORBIDDEN");

        api.get("/notifications", api.login("student02@example.com")).andExpect(jsonPath("$", hasSize(1)));
    }

    private String contributionsPath() {
        return "/teams/" + team1.getId() + "/contributions";
    }

    private String createBody(String title, Long teamMemberId) {
        return """
                { "title": "%s", "teamMemberId": %d }
                """.formatted(title, teamMemberId);
    }

    // 로그인한 회원의 알림 목록에서 맨 위(가장 최근) 알림 번호를 꺼냅니다.
    private int firstNotificationId(String token) throws Exception {
        String list = api.get("/notifications", token).andReturn().getResponse().getContentAsString();
        return JsonPath.read(list, "$[0].id");
    }
}
