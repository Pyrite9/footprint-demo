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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 교수의 제출 현황, 평가 결과, CSV 규칙을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 시작 데이터의 평가 단계 과제 1조(student01~03)와 기록 단계 1조를 씁니다.
// @Transactional: 테스트가 끝나면 데이터베이스 변경을 되돌립니다. 그래서 테스트끼리 서로 영향을 주지 않습니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EvaluationResultRuleTests {

    private final TestApi api;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    private List<TeamMember> members;
    private String evaluationsPath;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    EvaluationResultRuleTests(MockMvc mockMvc, TeamRepository teamRepository, TeamMemberRepository teamMemberRepository) {
        this.api = new TestApi(mockMvc);
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    // 평가 단계 조의 조원 3명을 찾아 둡니다.
    @BeforeEach
    void findSeedTeam() {
        Team team = teamRepository.findByInviteCode("demo-evaluation-1").orElseThrow();
        members = teamMemberRepository.findByTeamIdOrderByIdAsc(team.getId());
        evaluationsPath = "/teams/" + team.getId() + "/evaluations";
    }

    // 제출 현황은 조원 전원을 한 줄씩 보여 줍니다. 임시 저장만 한 조원은 미제출입니다.
    @Test
    void statusShowsEveryMember() throws Exception {
        String professor = api.login("professor@example.com");
        api.get(evaluationsPath + "/status", professor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].submitted").value(false))
                .andExpect(jsonPath("$[0].submittedAt").isEmpty());

        submit("student02@example.com", members.get(1), "3", "null", "null");
        api.put(evaluationsPath + "/draft", api.login("student03@example.com"),
                "{ \"evaluations\": [" + item(members.get(0), "4", "null") + "] }").andExpect(status().isOk());

        api.get(evaluationsPath + "/status", professor)
                .andExpect(jsonPath("$[0].name").value("김민준"))
                .andExpect(jsonPath("$[0].submitted").value(false))
                .andExpect(jsonPath("$[1].teamMemberId").value(members.get(1).getId()))
                .andExpect(jsonPath("$[1].name").value("이서연"))
                .andExpect(jsonPath("$[1].submitted").value(true))
                .andExpect(jsonPath("$[1].submittedAt").isNotEmpty())
                .andExpect(jsonPath("$[2].submitted").value(false));
    }

    // 평가 결과는 평가자별 점수와 의견을 입력한 그대로 돌려줍니다. 임시 저장은 나오지 않고 합계, 평균, 순위는 없습니다.
    @Test
    void resultsShowScoresAsEntered() throws Exception {
        submit("student01@example.com", members.get(0), "5", "\"잘 도와줬습니다\"", "null");
        api.put(evaluationsPath + "/draft", api.login("student03@example.com"),
                "{ \"evaluations\": [" + item(members.get(0), "1", "\"임시 의견\"") + "] }").andExpect(status().isOk());

        api.get(evaluationsPath, api.login("professor@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].evaluatorTeamMemberId").value(members.get(0).getId()))
                .andExpect(jsonPath("$[0].evaluatorName").value("김민준"))
                .andExpect(jsonPath("$[0].targetTeamMemberId").value(members.get(1).getId()))
                .andExpect(jsonPath("$[0].targetName").value("이서연"))
                .andExpect(jsonPath("$[0].goalSharing").value(5))
                .andExpect(jsonPath("$[0].progressCheck").value(5))
                .andExpect(jsonPath("$[0].comment").value("잘 도와줬습니다"))
                .andExpect(jsonPath("$[0].submittedAt").isNotEmpty())
                .andExpect(jsonPath("$[1].comment").isEmpty())
                .andExpect(jsonPath("$[0].total").doesNotExist())
                .andExpect(jsonPath("$[0].average").doesNotExist())
                .andExpect(jsonPath("$[0].rank").doesNotExist());
    }

    // 세 API 모두 과제를 만든 교수만 부릅니다. 제출한 조원도, 다른 조 학생도 403입니다.
    @Test
    void onlyProfessorOfAssignmentCanRead() throws Exception {
        for (String email : List.of("student01@example.com", "student02@example.com", "student06@example.com")) {
            String token = api.login(email);
            for (String path : List.of("/status", "", "/export")) {
                TestApi.expectError(api.get(evaluationsPath + path, token), 403, "FORBIDDEN");
            }
        }
    }

    // 없는 조는 404, token이 없으면 401입니다.
    @Test
    void unknownTeamAndNoToken() throws Exception {
        String professor = api.login("professor@example.com");
        for (String path : List.of("/status", "", "/export")) {
            TestApi.expectError(api.get("/teams/999999/evaluations" + path, professor), 404, "NOT_FOUND");
            TestApi.expectError(api.getWithoutToken(evaluationsPath + path), 401, "UNAUTHORIZED");
        }
    }

    // 기록 단계에도 부를 수 있습니다. 제출이 없으니 모두 미제출이고 결과는 비어 있으며 CSV는 제목 줄뿐입니다.
    @Test
    void recordingPhaseHasNoResults() throws Exception {
        Team recordingTeam = teamRepository.findByInviteCode("demo-team-1").orElseThrow();
        String path = "/teams/" + recordingTeam.getId() + "/evaluations";
        String professor = api.login("professor@example.com");

        api.get(path + "/status", professor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].submitted").value(false));
        api.get(path, professor).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        assertEquals(1, exportLines(path + "/export", professor).size());
    }

    // CSV는 BOM으로 시작하고 합계, 평균, 순위 열이 없습니다. 첨부 파일로 내려갑니다.
    @Test
    void csvHasBomAndFixedColumns() throws Exception {
        submit("student01@example.com", members.get(0), "4", "null", "null");
        String professor = api.login("professor@example.com");

        var response = api.get(evaluationsPath + "/export", professor)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"evaluations-team-" + members.get(0).getTeam().getId() + ".csv\""))
                .andReturn().getResponse();

        byte[] bytes = response.getContentAsByteArray();
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);
        String text = new String(bytes, StandardCharsets.UTF_8).substring(1);
        String[] lines = text.split("\r\n");
        assertEquals("평가자,대상,목표 공유·협업,역할 수행,격려·도움,온라인 소통,대면 소통,진행 점검·보충,의견,제출 시각", lines[0]);
        assertTrue(lines[1].startsWith("김민준,이서연,4,4,4,4,4,4,,"), lines[1]);
        assertTrue(lines[1].matches(".*,\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"), lines[1]);
        assertFalse(lines[0].contains("합계") || lines[0].contains("평균") || lines[0].contains("순위"));
    }

    // =, +, -, @로 시작하는 의견은 수식으로 실행되지 않게 앞에 '를 붙입니다. 쉼표, 큰따옴표, 줄바꿈은 칸이 나뉘지 않게 감쌉니다.
    @Test
    void csvEscapesFormulaAndSeparators() throws Exception {
        submit("student01@example.com", members.get(0), "3", "\"=SUM(A1:A9)\"", "\"+1 도움\"");
        submit("student02@example.com", members.get(1), "3", "\"-2 의견\"", "\"@멘션\"");
        submit("student03@example.com", members.get(2), "3", "\"쉼표, \\\"따옴표\\\"\"", "\"첫 줄\\n둘째 줄\"");

        String text = exportText(evaluationsPath + "/export", api.login("professor@example.com"));

        assertTrue(text.contains(",'=SUM(A1:A9),"), text);
        assertTrue(text.contains(",'+1 도움,"), text);
        assertTrue(text.contains(",'-2 의견,"), text);
        assertTrue(text.contains(",'@멘션,"), text);
        assertTrue(text.contains(",\"쉼표, \"\"따옴표\"\"\","), text);
        assertTrue(text.contains(",\"첫 줄\n둘째 줄\","), text);
    }

    // 이 조원의 평가를 나머지 두 조원에게 제출합니다. 대상 순서는 조원 번호 순이고 의견을 하나씩 받습니다.
    private void submit(String email, TeamMember evaluator, String score, String firstComment, String secondComment) throws Exception {
        List<String> items = new ArrayList<>();
        for (TeamMember target : members) {
            if (!target.getId().equals(evaluator.getId())) {
                items.add(item(target, score, items.isEmpty() ? firstComment : secondComment));
            }
        }
        api.post(evaluationsPath, api.login(email), "{ \"evaluations\": [" + String.join(",", items) + "] }")
                .andExpect(status().isOk());
    }

    // 평가 한 건의 JSON입니다. comment는 따옴표까지 포함한 JSON 값입니다.
    private String item(TeamMember target, String score, String comment) {
        return """
                { "targetTeamMemberId": %d, "goalSharing": %s, "roleFulfillment": %s, "encouragement": %s,
                  "onlineCommunication": %s, "offlineCommunication": %s, "progressCheck": %s, "comment": %s }
                """.formatted(target.getId(), score, score, score, score, score, score, comment);
    }

    // CSV 내용을 BOM을 뺀 글자로 받습니다.
    private String exportText(String path, String token) throws Exception {
        byte[] bytes = api.get(path, token).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        return new String(bytes, StandardCharsets.UTF_8).substring(1);
    }

    // CSV 내용을 줄별로 받습니다.
    private List<String> exportLines(String path, String token) throws Exception {
        return List.of(exportText(path, token).split("\r\n"));
    }
}
