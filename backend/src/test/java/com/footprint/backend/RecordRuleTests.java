package com.footprint.backend;

import com.footprint.backend.entity.Deliverable;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.repository.DeliverableRepository;
import com.footprint.backend.repository.TeamMemberRepository;
import com.footprint.backend.repository.TeamRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 기록 열람 규칙을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 시작 데이터의 기록 단계 1조(student01~05)와 평가 단계 1조(student01~03)를 씁니다.
// @Transactional: 테스트가 끝나면 데이터베이스 변경을 되돌립니다. 그래서 테스트끼리 서로 영향을 주지 않습니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecordRuleTests {

    private final TestApi api;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final DeliverableRepository deliverableRepository;

    private Team recordingTeam;
    private Team evaluationTeam;
    private List<TeamMember> recordingMembers;
    private List<TeamMember> evaluationMembers;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    RecordRuleTests(MockMvc mockMvc, TeamRepository teamRepository, TeamMemberRepository teamMemberRepository,
                    DeliverableRepository deliverableRepository) {
        this.api = new TestApi(mockMvc);
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.deliverableRepository = deliverableRepository;
    }

    // 기록 단계 조와 평가 단계 조의 조원을 찾아 둡니다.
    @BeforeEach
    void findSeedTeams() {
        recordingTeam = teamRepository.findByInviteCode("demo-team-1").orElseThrow();
        evaluationTeam = teamRepository.findByInviteCode("demo-evaluation-1").orElseThrow();
        recordingMembers = teamMemberRepository.findByTeamIdOrderByIdAsc(recordingTeam.getId());
        evaluationMembers = teamMemberRepository.findByTeamIdOrderByIdAsc(evaluationTeam.getId());
    }

    // 조원과 과제를 만든 교수는 기록 단계와 평가 단계 모두 봅니다. 평가 시작 줄은 평가 단계부터 있습니다.
    @Test
    void membersAndProfessorSeeRecordsInBothPhases() throws Exception {
        for (String email : List.of("student01@example.com", "professor@example.com")) {
            String token = api.login(email);
            api.get(recordsPath(recordingTeam), token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(typeRows("EVALUATION_STARTED"), hasSize(0)));
            api.get(recordsPath(evaluationTeam), token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(typeRows("EVALUATION_STARTED"), hasSize(1)));
        }
    }

    // 다른 조 학생은 403, 없는 조는 404, token이 없으면 401입니다.
    @Test
    void outsiderIsRejected() throws Exception {
        TestApi.expectError(api.get(recordsPath(recordingTeam), api.login("student06@example.com")), 403, "FORBIDDEN");
        TestApi.expectError(api.get("/teams/999999/records", api.login("student01@example.com")), 404, "NOT_FOUND");
        TestApi.expectError(api.getWithoutToken(recordsPath(recordingTeam)), 401, "UNAUTHORIZED");
    }

    // 산출물, 기여 항목, 완료확인이 누가 무엇을 했는지와 함께 한 줄씩 펼쳐집니다. 저장 위치와 건수는 담지 않습니다.
    @Test
    void recordsAreFlattenedRowByRow() throws Exception {
        TeamMember student02 = evaluationMembers.get(1);

        api.get(recordsPath(evaluationTeam), api.login("student03@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(typeRows("DELIVERABLE_UPLOADED") + ".title", contains("최종 보고서.txt")))
                .andExpect(jsonPath(typeRows("DELIVERABLE_UPLOADED") + ".roleName", contains("백엔드")))
                .andExpect(jsonPath(typeRows("DELIVERABLE_UPLOADED") + ".teamMemberId", contains(student02.getId().intValue())))
                .andExpect(jsonPath(typeRows("DELIVERABLE_UPLOADED") + ".memberName", contains("이서연")))
                .andExpect(jsonPath(typeRows("CONTRIBUTION_CREATED") + ".title", contains("자료 취합")))
                .andExpect(jsonPath(typeRows("CONTRIBUTION_REPORTED") + ".memberName", contains("이서연")))
                .andExpect(jsonPath(typeRows("CONFIRMATION_CREATED") + ".memberName", contains("박도윤", "김민준")))
                .andExpect(jsonPath(typeRows("CONFIRMATION_CREATED") + ".targetMemberName", contains("이서연", "이서연")))
                .andExpect(jsonPath(typeRows("CONFIRMATION_CREATED") + ".title", contains("자료 취합", "최종 보고서.txt")))
                .andExpect(jsonPath("$[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$[0].reportMemo").doesNotExist())
                .andExpect(jsonPath("$[0].count").doesNotExist());
    }

    // 삭제와 취소는 원래 줄을 지우지 않고 줄을 하나 더합니다.
    @Test
    void deleteAndCancelAddRows() throws Exception {
        String leader = api.login("student01@example.com");
        String student02 = api.login("student02@example.com");
        Deliverable deliverable = deliverableRepository.findByTeamMemberIdOrderByIdDesc(recordingMembers.get(1).getId()).get(0);
        Deliverable leaderDeliverable = deliverableRepository.findByTeamMemberIdOrderByIdDesc(recordingMembers.get(0).getId()).get(0);

        String created = api.post("/teams/" + recordingTeam.getId() + "/contributions", leader,
                        "{ \"title\": \"취소할 항목\", \"teamMemberId\": " + recordingMembers.get(1).getId() + " }")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Integer contributionId = JsonPath.read(created, "$.id");
        api.post("/contributions/" + contributionId + "/cancel", leader, "").andExpect(status().isOk());

        String confirmed = api.post("/deliverables/" + leaderDeliverable.getId() + "/confirmations", student02, "")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Integer confirmationId = JsonPath.read(confirmed, "$.id");
        api.post("/confirmations/" + confirmationId + "/cancel", student02, "").andExpect(status().isOk());

        api.delete("/deliverables/" + deliverable.getId(), student02).andExpect(status().isOk());

        api.get(recordsPath(recordingTeam), leader)
                .andExpect(jsonPath(typeRows("DELIVERABLE_UPLOADED"), hasSize(2)))
                .andExpect(jsonPath(typeRows("DELIVERABLE_DELETED") + ".deliverableId", contains(deliverable.getId().intValue())))
                .andExpect(jsonPath(typeRows("CONTRIBUTION_CREATED") + ".title", hasItem("취소할 항목")))
                .andExpect(jsonPath(typeRows("CONTRIBUTION_CANCELED") + ".contributionId", contains(contributionId)))
                .andExpect(jsonPath(typeRows("CONFIRMATION_CREATED") + ".deliverableId", hasItem(leaderDeliverable.getId().intValue())))
                .andExpect(jsonPath(typeRows("CONFIRMATION_CANCELED") + ".memberName", contains("이서연")));
    }

    // 역할을 바꿔도 첫 지정 줄은 처음 역할과 처음 시각 그대로이고, 바뀔 때마다 이전 역할과 새 역할이 남습니다.
    @Test
    void roleRowsKeepFirstAssignment() throws Exception {
        TeamMember student02 = recordingMembers.get(1);
        // 처음 지정한 시각이 지금과 달라야 덮어쓰지 않았음을 알 수 있어서 시각을 옮겨 둡니다.
        ReflectionTestUtils.setField(student02, "roleAssignedAt", LocalDateTime.of(2000, 1, 1, 0, 0));
        String firstRoleName = student02.getRoleName();
        String leader = api.login("student01@example.com");
        String rolePath = "/teams/" + recordingTeam.getId() + "/members/" + student02.getId() + "/role";

        api.put(rolePath, leader, "{ \"roleName\": \"데이터\" }").andExpect(status().isOk());
        api.put(rolePath, leader, "{ \"roleName\": \"발표\" }").andExpect(status().isOk());

        String assigned = "$[?(@.type == 'ROLE_ASSIGNED' && @.teamMemberId == %d)]".formatted(student02.getId());
        String changed = "$[?(@.type == 'ROLE_CHANGED' && @.teamMemberId == %d)]".formatted(student02.getId());
        api.get(recordsPath(recordingTeam), leader)
                .andExpect(jsonPath(assigned + ".roleName", contains(firstRoleName)))
                .andExpect(jsonPath(assigned + ".occurredAt", contains("2000-01-01T00:00:00")))
                .andExpect(jsonPath(changed + ".roleName", contains("발표", "데이터")))
                .andExpect(jsonPath(changed + ".previousRoleName", contains("데이터", firstRoleName)));
    }

    // 조장 교체는 새 조장의 줄이고 이전 조장을 함께 담습니다.
    @Test
    void leaderChangeRowHasBothLeaders() throws Exception {
        TeamMember oldLeader = recordingMembers.get(0);
        TeamMember newLeader = recordingMembers.get(1);

        api.put("/teams/" + recordingTeam.getId() + "/leader", api.login("professor@example.com"),
                "{ \"teamMemberId\": " + newLeader.getId() + " }").andExpect(status().isOk());

        api.get(recordsPath(recordingTeam), api.login("student03@example.com"))
                .andExpect(jsonPath(typeRows("LEADER_CHANGED") + ".teamMemberId", contains(newLeader.getId().intValue())))
                .andExpect(jsonPath(typeRows("LEADER_CHANGED") + ".targetTeamMemberId", contains(oldLeader.getId().intValue())));
    }

    // 줄은 시각이 최근인 것부터입니다.
    @Test
    void newestRowComesFirst() throws Exception {
        List<Deliverable> deliverables = deliverableRepository.findByTeamMemberTeamIdOrderByIdDesc(recordingTeam.getId());
        Deliverable oldest = deliverables.get(0);
        Deliverable newest = deliverables.get(1);
        ReflectionTestUtils.setField(oldest, "uploadedAt", LocalDateTime.of(2000, 1, 1, 0, 0));
        ReflectionTestUtils.setField(newest, "uploadedAt", LocalDateTime.of(2100, 1, 1, 0, 0));

        api.get(recordsPath(recordingTeam), api.login("student01@example.com"))
                .andExpect(jsonPath("$[0].deliverableId").value(newest.getId()))
                .andExpect(jsonPath("$[-1:].deliverableId", contains(oldest.getId().intValue())));
    }

    // 평가 임시 저장과 평가 제출은 목록에 넣지 않습니다.
    @Test
    void evaluationIsNotListed() throws Exception {
        String token = api.login("student01@example.com");
        String path = recordsPath(evaluationTeam);
        String before = api.get(path, token).andReturn().getResponse().getContentAsString();

        List<String> items = new ArrayList<>();
        for (TeamMember target : evaluationMembers.subList(1, evaluationMembers.size())) {
            items.add("""
                    { "targetTeamMemberId": %d, "goalSharing": 3, "roleFulfillment": 3, "encouragement": 3,
                      "onlineCommunication": 3, "offlineCommunication": 3, "progressCheck": 3, "comment": null }
                    """.formatted(target.getId()));
        }
        String evaluationsPath = "/teams/" + evaluationTeam.getId() + "/evaluations";
        api.put(evaluationsPath + "/draft", token, "{ \"evaluations\": [" + items.get(0) + "] }").andExpect(status().isOk());
        assertEquals(before, api.get(path, token).andReturn().getResponse().getContentAsString());

        api.post(evaluationsPath, token, "{ \"evaluations\": [" + String.join(",", items) + "] }").andExpect(status().isOk());
        assertEquals(before, api.get(path, token).andReturn().getResponse().getContentAsString());
    }

    // 조의 기록 주소입니다.
    private String recordsPath(Team team) {
        return "/teams/" + team.getId() + "/records";
    }

    // 이 종류의 줄만 고르는 JSONPath입니다.
    private String typeRows(String type) {
        return "$[?(@.type == '" + type + "')]";
    }
}
