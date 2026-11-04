package com.footprint.backend;

import com.footprint.backend.entity.Confirmation;
import com.footprint.backend.entity.Contribution;
import com.footprint.backend.entity.Deliverable;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.repository.ConfirmationRepository;
import com.footprint.backend.repository.ContributionRepository;
import com.footprint.backend.repository.DeliverableRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 근거 패널 규칙을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 시작 데이터의 평가 단계 과제 1조(student01~03)를 씁니다. student02가 산출물과 기여 항목을 맡았고, student01과 student03이 하나씩 확인했습니다.
// @Transactional: 테스트가 끝나면 데이터베이스 변경을 되돌립니다. 그래서 테스트끼리 서로 영향을 주지 않습니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EvidenceRuleTests {

    private final TestApi api;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final DeliverableRepository deliverableRepository;
    private final ContributionRepository contributionRepository;
    private final ConfirmationRepository confirmationRepository;

    private Team team;
    private TeamMember student01;
    private TeamMember student02;
    private TeamMember student03;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    EvidenceRuleTests(MockMvc mockMvc, TeamRepository teamRepository, TeamMemberRepository teamMemberRepository,
                      DeliverableRepository deliverableRepository, ContributionRepository contributionRepository,
                      ConfirmationRepository confirmationRepository) {
        this.api = new TestApi(mockMvc);
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.deliverableRepository = deliverableRepository;
        this.contributionRepository = contributionRepository;
        this.confirmationRepository = confirmationRepository;
    }

    // 평가 단계 조의 조원 3명을 찾아 둡니다.
    @BeforeEach
    void findSeedTeam() {
        team = teamRepository.findByInviteCode("demo-evaluation-1").orElseThrow();
        List<TeamMember> members = teamMemberRepository.findByTeamIdOrderByIdAsc(team.getId());
        student01 = members.get(0);
        student02 = members.get(1);
        student03 = members.get(2);
    }

    // 평가 대상의 산출물, 기여 항목과 보고 메모, 그 기록에 남은 확인을 종류별로 돌려줍니다. 건수는 담지 않습니다.
    @Test
    void recordsOfTargetAreGivenByKind() throws Exception {
        api.get(evidencePath(student02), api.login("student01@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliverables", hasSize(1)))
                .andExpect(jsonPath("$.deliverables[0].fileName").value("최종 보고서.txt"))
                .andExpect(jsonPath("$.deliverables[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$.contributions", hasSize(1)))
                .andExpect(jsonPath("$.contributions[0].title").value("자료 취합"))
                .andExpect(jsonPath("$.contributions[0].reportMemo").value("자료를 모아 조 공유 폴더에 올렸습니다."))
                .andExpect(jsonPath("$.receivedConfirmations", hasSize(2)))
                .andExpect(jsonPath("$.receivedConfirmations[0].contribution.title").value("자료 취합"))
                .andExpect(jsonPath("$.receivedConfirmations[0].deliverable").isEmpty())
                .andExpect(jsonPath("$.receivedConfirmations[0].confirmerName").value("박도윤"))
                .andExpect(jsonPath("$.receivedConfirmations[1].deliverable.fileName").value("최종 보고서.txt"))
                .andExpect(jsonPath("$.receivedConfirmations[1].contribution").isEmpty())
                .andExpect(jsonPath("$.givenConfirmations", hasSize(0)))
                .andExpect(jsonPath("$.count").doesNotExist())
                .andExpect(jsonPath("$.total").doesNotExist());
    }

    // 대상이 다른 조원의 기록에 남긴 확인은 그 기록과 함께 돌려줍니다. 대상의 기록이 아닌 것은 다른 목록에 섞이지 않습니다.
    @Test
    void confirmationsGivenByTargetAreGiven() throws Exception {
        api.get(evidencePath(student01), api.login("student03@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliverables", hasSize(0)))
                .andExpect(jsonPath("$.contributions", hasSize(0)))
                .andExpect(jsonPath("$.receivedConfirmations", hasSize(0)))
                .andExpect(jsonPath("$.givenConfirmations", hasSize(1)))
                .andExpect(jsonPath("$.givenConfirmations[0].deliverable.fileName").value("최종 보고서.txt"))
                .andExpect(jsonPath("$.givenConfirmations[0].deliverable.uploaderName").value("이서연"))
                .andExpect(jsonPath("$.givenConfirmations[0].confirmerName").value("김민준"));
    }

    // 삭제된 산출물, 취소한 항목, 취소한 확인도 빼지 않고 시각과 함께 돌려줍니다.
    @Test
    void deletedAndCanceledRecordsStay() throws Exception {
        Deliverable deliverable = deliverableRepository.findByTeamMemberIdOrderByIdDesc(student02.getId()).get(0);
        deliverable.markDeleted();
        Confirmation confirmation = confirmationRepository.findByConfirmerIdOrderByIdDesc(student01.getId()).get(0);
        confirmation.cancel();
        Contribution canceled = contributionRepository.save(new Contribution(team, student02, "취소할 항목"));
        canceled.cancel();

        api.get(evidencePath(student02), api.login("student01@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliverables", hasSize(1)))
                .andExpect(jsonPath("$.deliverables[0].deletedAt").isNotEmpty())
                .andExpect(jsonPath("$.contributions", hasSize(2)))
                .andExpect(jsonPath("$.contributions[0].canceledAt").isNotEmpty())
                .andExpect(jsonPath("$.receivedConfirmations", hasSize(2)))
                .andExpect(jsonPath("$.receivedConfirmations[1].canceledAt").isNotEmpty());
    }

    // 자기 기록도 볼 수 있습니다. 교수도 평가 단계에서 볼 수 있습니다.
    @Test
    void selfAndProfessorCanSee() throws Exception {
        api.get(evidencePath(student02), api.login("student02@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliverables", hasSize(1)));
        api.get(evidencePath(student02), api.login("professor@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliverables", hasSize(1)));
    }

    // 기록 단계에서 조원은 400이고, 과제를 만든 교수는 볼 수 있습니다.
    @Test
    void memberCannotSeeInRecordingButProfessorCan() throws Exception {
        Team recordingTeam = teamRepository.findByInviteCode("demo-team-1").orElseThrow();
        TeamMember leader = teamMemberRepository.findByTeamIdOrderByIdAsc(recordingTeam.getId()).get(0);
        String path = "/teams/" + recordingTeam.getId() + "/members/" + leader.getId() + "/evidence";

        api.get(path, api.login("student02@example.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("근거 패널은 평가 단계에서 볼 수 있습니다"));
        api.get(path, api.login("professor@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliverables", hasSize(1)));
    }

    // 다른 조 학생은 403입니다. 토큰이 없으면 401입니다.
    @Test
    void outsiderIsForbidden() throws Exception {
        TestApi.expectError(api.get(evidencePath(student02), api.login("student06@example.com")), 403, "FORBIDDEN");
        TestApi.expectError(api.getWithoutToken(evidencePath(student02)), 401, "UNAUTHORIZED");
    }

    // 없는 조, 그 조의 조원이 아닌 번호는 404입니다. 숫자가 아닌 번호는 400입니다.
    @Test
    void wrongAddressIsRejected() throws Exception {
        String token = api.login("student01@example.com");
        Team otherTeam = teamRepository.findByInviteCode("demo-team-2").orElseThrow();
        TeamMember otherTeamMember = teamMemberRepository.findByTeamIdOrderByIdAsc(otherTeam.getId()).get(0);

        TestApi.expectError(api.get(evidencePath(otherTeamMember), token), 404, "NOT_FOUND");
        TestApi.expectError(api.get("/teams/999999/members/" + student02.getId() + "/evidence", token), 404, "NOT_FOUND");
        TestApi.expectError(api.get("/teams/" + team.getId() + "/members/abc/evidence", token), 400, "INVALID_INPUT");
    }

    // 평가 단계 조에서 한 조원의 근거 주소를 만듭니다.
    private String evidencePath(TeamMember target) {
        return "/teams/" + team.getId() + "/members/" + target.getId() + "/evidence";
    }
}
