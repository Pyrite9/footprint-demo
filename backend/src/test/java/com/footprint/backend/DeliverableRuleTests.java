package com.footprint.backend;

import com.footprint.backend.entity.Team;
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
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 산출물 업로드 규칙을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 올린 파일은 시험 전용 버킷(footprint-test)에 남습니다. 데이터베이스 변경은 테스트가 끝나면 되돌립니다(@Transactional).
// 20MB 제한은 여기서 확인하지 않습니다. MockMvc는 실제 Tomcat을 거치지 않아서 크기 제한이 동작하지 않습니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DeliverableRuleTests {

    private final TestApi api;
    private final TeamRepository teamRepository;

    private String deliverablesPath;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    DeliverableRuleTests(MockMvc mockMvc, TeamRepository teamRepository) {
        this.api = new TestApi(mockMvc);
        this.teamRepository = teamRepository;
    }

    // 시작 데이터의 1조 산출물 주소를 만들어 둡니다.
    @BeforeEach
    void findSeedTeam() {
        Team team = teamRepository.findByInviteCode("demo-team-1").orElseThrow();
        deliverablesPath = "/teams/" + team.getId() + "/deliverables";
    }

    // 역할이 있는 조원이 올리면 그때의 역할명이 복사되고, 받은 파일은 올린 내용과 같습니다.
    @Test
    void uploadCopiesRoleNameAndKeepsContent() throws Exception {
        String token = api.login("student02@example.com");
        MockMultipartFile file = textFile("API 명세.txt", "발자국 업로드 시험");

        String response = api.upload(deliverablesPath, token, file)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleName").value("백엔드"))
                .andExpect(jsonPath("$.fileName").value("API 명세.txt"))
                .andExpect(jsonPath("$.uploadedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        int deliverableId = JsonPath.read(response, "$.id");
        byte[] downloaded = api.get("/deliverables/" + deliverableId + "/download", token)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(file.getBytes(), downloaded);
    }

    // 같은 이름으로 다시 올려도 덮어쓰지 않고 새 기록이 앞에 생깁니다. 시작 데이터 2건에 2건이 더해집니다.
    @Test
    void reuploadDoesNotOverwrite() throws Exception {
        String token = api.login("student02@example.com");

        String first = api.upload(deliverablesPath, token, textFile("같은 이름.txt", "첫 번째"))
                .andReturn().getResponse().getContentAsString();
        String second = api.upload(deliverablesPath, token, textFile("같은 이름.txt", "두 번째"))
                .andReturn().getResponse().getContentAsString();

        int firstId = JsonPath.read(first, "$.id");
        int secondId = JsonPath.read(second, "$.id");
        api.get(deliverablesPath, token)
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].id").value(secondId))
                .andExpect(jsonPath("$[1].id").value(firstId));
    }

    // 교수와 다른 조 학생은 올릴 수 없습니다.
    @Test
    void outsiderCannotUpload() throws Exception {
        MockMultipartFile file = textFile("시험.txt", "내용");

        TestApi.expectError(api.upload(deliverablesPath, api.login("professor@example.com"), file), 403, "FORBIDDEN");
        TestApi.expectError(api.upload(deliverablesPath, api.login("student06@example.com"), file), 403, "FORBIDDEN");
    }

    // 역할이 배정되지 않은 조원은 올릴 수 없습니다. 새 과제의 조장은 아직 역할이 없습니다.
    @Test
    void memberWithoutRoleCannotUpload() throws Exception {
        String professor = api.login("professor@example.com");
        String created = api.post("/assignments", professor, """
                        { "title": "시험 과제", "startDate": "2026-11-02", "endDate": "2026-12-11",
                          "leaderEmails": ["student01@example.com"] }
                        """)
                .andReturn().getResponse().getContentAsString();
        int assignmentId = JsonPath.read(created, "$.id");
        String detail = api.get("/assignments/" + assignmentId, professor).andReturn().getResponse().getContentAsString();
        int teamId = JsonPath.read(detail, "$.teams[0].id");

        TestApi.expectError(api.upload("/teams/" + teamId + "/deliverables", api.login("student01@example.com"),
                textFile("시험.txt", "내용")), 403, "FORBIDDEN");
    }

    // 파일이 빠졌거나 빈 파일이면 400입니다.
    @Test
    void missingOrEmptyFileIsRejected() throws Exception {
        String token = api.login("student02@example.com");

        TestApi.expectError(api.upload(deliverablesPath, token, null), 400, "INVALID_INPUT");
        TestApi.expectError(api.upload(deliverablesPath, token, textFile("빈 파일.txt", "")), 400, "INVALID_INPUT");
    }

    private MockMultipartFile textFile(String fileName, String text) {
        return new MockMultipartFile("file", fileName, "text/plain", text.getBytes(StandardCharsets.UTF_8));
    }
}
