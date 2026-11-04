package com.footprint.backend;

import com.footprint.backend.entity.Deliverable;
import com.footprint.backend.entity.Member;
import com.footprint.backend.entity.MemberType;
import com.footprint.backend.entity.Team;
import com.footprint.backend.repository.DeliverableRepository;
import com.footprint.backend.repository.MemberRepository;
import com.footprint.backend.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 접근 범위 규칙을 실제 요청으로 확인합니다. "그 조의 조원이거나 그 과제를 만든 교수"만 조의 기록에 접근합니다.
// PostgreSQL과 MinIO가 켜져 있어야 합니다. 시작 데이터(1조 student01~05, 2조 student06~10)를 씁니다.
// @Transactional: 테스트가 끝나면 데이터베이스 변경을 되돌립니다. 그래서 테스트끼리 서로 영향을 주지 않습니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccessRuleTests {

    private final TestApi api;
    private final TeamRepository teamRepository;
    private final DeliverableRepository deliverableRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    private Team team1;
    private Team team2;
    private Long team1DeliverableId;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    AccessRuleTests(MockMvc mockMvc, TeamRepository teamRepository, DeliverableRepository deliverableRepository,
                    MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.api = new TestApi(mockMvc);
        this.teamRepository = teamRepository;
        this.deliverableRepository = deliverableRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 시작 데이터의 조와 산출물을 찾아 둡니다. id는 시작 데이터 순서에 따라 바뀔 수 있어서 초대 코드로 찾습니다.
    @BeforeEach
    void findSeedData() {
        team1 = teamRepository.findByInviteCode("demo-team-1").orElseThrow();
        team2 = teamRepository.findByInviteCode("demo-team-2").orElseThrow();
        List<Deliverable> deliverables = deliverableRepository.findByTeamMemberTeamIdOrderByIdDesc(team1.getId());
        team1DeliverableId = deliverables.get(0).getId();
    }

    // token이 없으면 401입니다.
    @Test
    void noTokenIsUnauthorized() throws Exception {
        TestApi.expectError(api.getWithoutToken("/members/me"), 401, "UNAUTHORIZED");
    }

    // 다른 조 학생이 주소를 직접 입력해도 조의 기록은 모두 403입니다.
    @Test
    void otherTeamStudentIsForbidden() throws Exception {
        String token = api.login("student06@example.com");

        for (String path : team1RecordPaths()) {
            TestApi.expectError(api.get(path, token), 403, "FORBIDDEN");
        }
    }

    // 그 과제를 만들지 않은 교수도 조의 기록은 모두 403입니다.
    @Test
    void otherProfessorIsForbidden() throws Exception {
        memberRepository.save(new Member("other-professor@example.com", passwordEncoder.encode("pass1234"),
                "다른교수", MemberType.PROFESSOR));
        String token = api.login("other-professor@example.com");

        for (String path : team1RecordPaths()) {
            TestApi.expectError(api.get(path, token), 403, "FORBIDDEN");
        }
        TestApi.expectError(api.get("/assignments/" + team1.getAssignment().getId(), token), 403, "FORBIDDEN");
    }

    // 그 조의 조원과 그 과제를 만든 교수는 조의 기록을 모두 받습니다.
    @Test
    void teamMemberAndItsProfessorAreAllowed() throws Exception {
        for (String email : List.of("student02@example.com", "professor@example.com")) {
            String token = api.login(email);
            for (String path : team1RecordPaths()) {
                api.get(path, token).andExpect(status().isOk());
            }
        }
    }

    // 과제 상세의 조 목록은 교수에게 조 전부, 학생에게 자기 조만 나옵니다.
    @Test
    void assignmentDetailShowsOnlyVisibleTeams() throws Exception {
        String path = "/assignments/" + team1.getAssignment().getId();

        api.get(path, api.login("professor@example.com"))
                .andExpect(jsonPath("$.teams.length()").value(2));
        api.get(path, api.login("student06@example.com"))
                .andExpect(jsonPath("$.teams.length()").value(1))
                .andExpect(jsonPath("$.teams[0].id").value(team2.getId()));
    }

    // 없는 조는 404입니다.
    @Test
    void missingTeamIsNotFound() throws Exception {
        TestApi.expectError(api.get("/teams/999999", api.login("student01@example.com")), 404, "NOT_FOUND");
    }

    // 등록한 과제를 지우는 API는 없습니다. 주소는 있지만 method가 없는 요청은 404입니다.
    @Test
    void assignmentCannotBeDeleted() throws Exception {
        String path = "/assignments/" + team1.getAssignment().getId();

        TestApi.expectError(api.delete(path, api.login("professor@example.com")), 404, "NOT_FOUND");
    }

    // 접근 범위 검사를 받는 1조의 기록 주소입니다.
    private List<String> team1RecordPaths() {
        Long teamId = team1.getId();
        return List.of("/teams/" + teamId, "/teams/" + teamId + "/members", "/teams/" + teamId + "/deliverables",
                "/deliverables/" + team1DeliverableId + "/download");
    }
}
