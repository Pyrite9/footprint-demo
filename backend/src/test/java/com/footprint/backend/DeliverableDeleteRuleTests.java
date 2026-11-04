package com.footprint.backend;

import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.exception.NotFoundException;
import com.footprint.backend.repository.DeliverableRepository;
import com.footprint.backend.repository.TeamMemberRepository;
import com.footprint.backend.repository.TeamRepository;
import com.footprint.backend.service.StorageService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 산출물 삭제 규칙을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 데이터베이스 변경은 테스트가 끝나면 되돌아가지만 MinIO 삭제는 되돌아가지 않습니다.
// 그래서 시작 산출물을 지우지 않고, 테스트마다 새로 올린 파일을 지웁니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DeliverableDeleteRuleTests {

    private final TestApi api;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final DeliverableRepository deliverableRepository;
    private final StorageService storageService;

    private Long teamId;
    private String deliverablesPath;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    DeliverableDeleteRuleTests(MockMvc mockMvc, TeamRepository teamRepository, TeamMemberRepository teamMemberRepository,
                               DeliverableRepository deliverableRepository, StorageService storageService) {
        this.api = new TestApi(mockMvc);
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.deliverableRepository = deliverableRepository;
        this.storageService = storageService;
    }

    // 시작 데이터의 1조 산출물 주소를 만들어 둡니다.
    @BeforeEach
    void findSeedTeam() {
        Team team = teamRepository.findByInviteCode("demo-team-1").orElseThrow();
        teamId = team.getId();
        deliverablesPath = "/teams/" + teamId + "/deliverables";
    }

    // 올린 조원이 지우면 파일 본문은 사라지고, 행은 삭제 시각과 함께 목록에 남습니다. 시작 산출물 2건과 합쳐 3건 그대로입니다.
    @Test
    void uploaderDeletesFileAndRowRemains() throws Exception {
        String member = api.login("student02@example.com");
        int id = uploadBy(member, "지울 파일.txt");
        String storageKey = deliverableRepository.findById((long) id).orElseThrow().getStorageKey();

        api.delete("/deliverables/" + id, member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.fileName").value("지울 파일.txt"))
                .andExpect(jsonPath("$.roleName").value("백엔드"))
                // 서버가 초까지 기록합니다. 다른 기록 시각, 가짜 API와 같은 모양입니다.
                .andExpect(jsonPath("$.deletedAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));

        api.get(deliverablesPath, member)
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].deletedAt").isNotEmpty())
                .andExpect(jsonPath("$[1].deletedAt").isEmpty())
                .andExpect(jsonPath("$[2].deletedAt").isEmpty());
        assertThrows(NotFoundException.class, () -> storageService.load(storageKey));
    }

    // 삭제된 산출물은 조원도 교수도 받을 수 없습니다. 권한이 없는 사람은 삭제 여부를 알 수 없게 403입니다.
    @Test
    void deletedDeliverableCannotBeDownloaded() throws Exception {
        String member = api.login("student02@example.com");
        int id = uploadBy(member, "지울 파일.txt");
        api.delete("/deliverables/" + id, member).andExpect(status().isOk());
        String downloadPath = "/deliverables/" + id + "/download";

        expectNotFound(api.get(downloadPath, member), "삭제된 산출물입니다. id=" + id);
        expectNotFound(api.get(downloadPath, api.login("professor@example.com")), "삭제된 산출물입니다. id=" + id);
        TestApi.expectError(api.get(downloadPath, api.login("student06@example.com")), 403, "FORBIDDEN");
    }

    // 올린 조원만 지웁니다. 조장, 다른 조원, 다른 조 학생, 교수는 403이고 파일은 그대로입니다.
    @Test
    void onlyUploaderDeletes() throws Exception {
        String member = api.login("student02@example.com");
        int id = uploadBy(member, "남을 파일.txt");

        for (String email : List.of("student01@example.com", "student03@example.com", "student06@example.com", "professor@example.com")) {
            TestApi.expectError(api.delete("/deliverables/" + id, api.login(email)), 403, "FORBIDDEN");
        }

        api.get("/deliverables/" + id + "/download", member).andExpect(status().isOk());
    }

    // 역할이 바뀌어도 올린 사람이 지울 수 있고, 지운 줄은 올릴 때의 역할로 남습니다.
    @Test
    void uploaderDeletesAfterRoleChange() throws Exception {
        String member = api.login("student02@example.com");
        int id = uploadBy(member, "역할 변경 전.txt");
        TeamMember teamMember = teamMemberRepository.findByTeamIdOrderByIdAsc(teamId).get(1);
        api.put("/teams/" + teamId + "/members/" + teamMember.getId() + "/role", api.login("student01@example.com"),
                "{ \"roleName\": \"데이터\" }").andExpect(status().isOk());

        api.delete("/deliverables/" + id, member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleName").value("백엔드"));
    }

    // 이미 지운 산출물을 다시 지우면 400이고, 처음 지운 시각은 그대로입니다.
    @Test
    void deletingTwiceIsRejected() throws Exception {
        String member = api.login("student02@example.com");
        int id = uploadBy(member, "지울 파일.txt");
        String deleted = api.delete("/deliverables/" + id, member).andReturn().getResponse().getContentAsString();
        String deletedAt = JsonPath.read(deleted, "$.deletedAt");

        ResultActions again = api.delete("/deliverables/" + id, member);

        TestApi.expectError(again, 400, "INVALID_INPUT");
        again.andExpect(jsonPath("$.message").value("이미 삭제된 산출물입니다"));
        api.get(deliverablesPath, member).andExpect(jsonPath("$[0].deletedAt").value(deletedAt));
    }

    // 없는 산출물은 404, 번호가 숫자가 아니면 400입니다.
    @Test
    void wrongIdIsRejected() throws Exception {
        String member = api.login("student02@example.com");

        expectNotFound(api.delete("/deliverables/999999", member), "산출물이 없습니다. id=999999");
        TestApi.expectError(api.delete("/deliverables/abc", member), 400, "INVALID_INPUT");
    }

    // 목록 응답에 올린 사람의 회원 번호가 있습니다. 화면이 내 산출물에만 삭제 버튼을 보여 줄 때 씁니다.
    @Test
    void listShowsUploaderMemberId() throws Exception {
        String member = api.login("student02@example.com");
        Long memberId = teamMemberRepository.findByTeamIdOrderByIdAsc(teamId).get(1).getMember().getId();
        uploadBy(member, "내 파일.txt");

        api.get(deliverablesPath, member).andExpect(jsonPath("$[0].uploaderMemberId").value(memberId));
    }

    // 파일 하나를 올리고 산출물 번호를 돌려줍니다.
    private int uploadBy(String token, String fileName) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain", "삭제 시험".getBytes(StandardCharsets.UTF_8));
        String response = api.upload(deliverablesPath, token, file)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    // 404 NOT_FOUND이고 메시지가 같은지 확인합니다.
    private void expectNotFound(ResultActions result, String message) throws Exception {
        TestApi.expectError(result, 404, "NOT_FOUND");
        result.andExpect(jsonPath("$.message").value(message));
    }
}
