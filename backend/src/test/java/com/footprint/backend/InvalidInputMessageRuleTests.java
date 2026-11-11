package com.footprint.backend;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 400 응답의 message에 틀린 칸을 담는 규칙(X-7)을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 응답 모양은 그대로 { code, message }이고, message 끝에 "칸 이름: 이유"를 붙입니다. 가짜 API도 같은 문장을 냅니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InvalidInputMessageRuleTests {

    private final TestApi api;

    @Autowired
    InvalidInputMessageRuleTests(MockMvc mockMvc) {
        this.api = new TestApi(mockMvc);
    }

    // @Valid에 걸린 칸은 모두 적고, "칸 이름: 이유" 글자 순으로 정렬합니다. JSON에 적은 순서가 달라도 문장은 같습니다.
    @Test
    void validationErrorsNameEveryFieldInFixedOrder() throws Exception {
        String professor = api.login("professor@example.com");
        String expected = "입력값이 올바르지 않습니다. endDate: 값이 없습니다, leaderEmails: 비어 있습니다, "
                + "startDate: 값이 없습니다, title: 비어 있습니다";

        expectMessage(api.post("/assignments", professor, "{}"), expected);
        expectMessage(api.post("/assignments", professor, """
                { "leaderEmails": [], "title": " " }
                """), expected);
        expectMessage(api.post("/assignments", professor, """
                { "title": "", "leaderEmails": null }
                """), expected);
    }

    // 한 칸이 두 검사에 걸리면 두 이유를 모두 적습니다. 길이 제한은 그 숫자를 적습니다.
    @Test
    void oneFieldCanHaveTwoReasons() throws Exception {
        String body = """
                { "title": "%s", "startDate": "2026-11-02", "endDate": "2026-12-11", "leaderEmails": ["student01@example.com"] }
                """.formatted(" ".repeat(256));

        expectMessage(api.post("/assignments", api.login("professor@example.com"), body),
                "입력값이 올바르지 않습니다. title: 255자 이하여야 합니다, title: 비어 있습니다");
    }

    // 목록 안의 칸은 "목록[번호].칸" 모양으로 적습니다. 점수 범위와 의견 길이도 그 숫자를 적습니다.
    @Test
    void listItemsAreNamedWithIndex() throws Exception {
        String body = """
                { "evaluations": [
                    { "targetTeamMemberId": 2, "goalSharing": 0, "roleFulfillment": 6, "encouragement": 3,
                      "onlineCommunication": 3, "offlineCommunication": 3, "progressCheck": 3, "comment": "%s" },
                    null
                ] }
                """.formatted("가".repeat(2001));

        expectMessage(api.post("/teams/1/evaluations", api.login("student01@example.com"), body),
                "입력값이 올바르지 않습니다. evaluations[0].comment: 2000자 이하여야 합니다, "
                        + "evaluations[0].goalSharing: 1 이상이어야 합니다, evaluations[0].roleFulfillment: 5 이하여야 합니다, "
                        + "evaluations[1]: 값이 없습니다");
    }

    // 칸의 타입이 틀리면 JSON을 읽는 단계에서 거절하고, 처음 틀린 칸 하나를 적습니다.
    @Test
    void bodyTypeErrorNamesFirstWrongField() throws Exception {
        String professor = api.login("professor@example.com");
        String student = api.login("student01@example.com");

        expectMessage(api.post("/teams/1/evaluations", student, """
                { "evaluations": [ { "targetTeamMemberId": 2 }, { "goalSharing": 4.5 } ] }
                """), "요청 body의 형식이 올바르지 않습니다. evaluations[1].goalSharing: 정수여야 합니다");
        expectMessage(api.put("/teams/1/leader", professor, """
                { "teamMemberId": "5" }
                """), "요청 body의 형식이 올바르지 않습니다. teamMemberId: 정수여야 합니다");
        // 틀린 칸이 둘이면 JSON에 먼저 적힌 칸입니다.
        expectMessage(api.post("/assignments", professor, """
                { "endDate": "2026-13-01", "startDate": "", "leaderEmails": "student01@example.com" }
                """), "요청 body의 형식이 올바르지 않습니다. endDate: yyyy-MM-dd 형식의 날짜여야 합니다");
        expectMessage(api.post("/assignments", professor, """
                { "leaderEmails": "student01@example.com", "endDate": "2026-13-01" }
                """), "요청 body의 형식이 올바르지 않습니다. leaderEmails: 배열이어야 합니다");
        expectMessage(api.post("/teams/1/evaluations", student, """
                { "evaluations": [ 5 ] }
                """), "요청 body의 형식이 올바르지 않습니다. evaluations[0]: 형식이 올바르지 않습니다");
    }

    // JSON 문법이 깨졌거나 body가 JSON 객체가 아니면 칸을 알 수 없어서 앞 문장만 보냅니다. Jackson의 원문 메시지는 내보내지 않습니다.
    @Test
    void brokenBodyHasNoFieldAndNoInternalDetail() throws Exception {
        String professor = api.login("professor@example.com");

        expectMessage(api.post("/assignments", professor, "{ \"title\": "), "요청 body의 형식이 올바르지 않습니다");
        expectMessage(api.post("/assignments", professor, "[]"), "요청 body의 형식이 올바르지 않습니다");
        expectMessage(api.post("/assignments", professor, ""), "요청 body의 형식이 올바르지 않습니다");
    }

    // 주소의 값이 숫자가 아니면 주소 자리의 이름을 적습니다.
    @Test
    void pathTypeErrorNamesPathVariable() throws Exception {
        String professor = api.login("professor@example.com");

        expectMessage(api.get("/assignments/abc", professor), "주소의 값이 올바르지 않습니다. assignmentId: 정수여야 합니다");
        expectMessage(api.get("/teams/1/members/x/evidence", professor), "주소의 값이 올바르지 않습니다. teamMemberId: 정수여야 합니다");
    }

    // 업로드에 file 칸이 빠졌으면 file을 적습니다.
    @Test
    void missingFilePartIsNamed() throws Exception {
        expectMessage(api.upload("/teams/1/deliverables", api.login("student01@example.com"), null),
                "입력값이 올바르지 않습니다. file: 값이 없습니다");
    }

    // Service가 던지는 업무 규칙 위반의 문장은 그대로입니다.
    @Test
    void serviceMessagesStayAsTheyAre() throws Exception {
        expectMessage(api.post("/assignments", api.login("professor@example.com"), """
                { "title": "시험 과제", "startDate": "2026-12-11", "endDate": "2026-11-02", "leaderEmails": ["student01@example.com"] }
                """), "종료일이 시작일보다 앞입니다");
    }

    // 400 INVALID_INPUT이고, body는 code와 message 두 칸뿐이며, message가 기대한 문장과 같은지 확인합니다.
    // 서버 내부 정보(클래스 이름, Jackson 원문)가 섞이지 않았는지도 함께 봅니다.
    private static void expectMessage(ResultActions result, String expected) throws Exception {
        String content = result.andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> body = JsonPath.read(content, "$");

        assertThat(body).containsOnlyKeys("code", "message");
        assertThat(body.get("code")).isEqualTo("INVALID_INPUT");
        assertThat(body.get("message")).isEqualTo(expected);
        assertThat(content).doesNotContain("java.", "com.footprint", "Exception", "JSON parse", "Cannot ");
    }
}
