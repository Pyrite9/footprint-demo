package com.footprint.backend;

import com.footprint.backend.config.DataInitializer;
import com.footprint.backend.entity.Confirmation;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.repository.ConfirmationRepository;
import com.footprint.backend.repository.ContributionRepository;
import com.footprint.backend.repository.DeliverableRepository;
import com.footprint.backend.repository.MemberRepository;
import com.footprint.backend.repository.NotificationRepository;
import com.footprint.backend.repository.TeamMemberRepository;
import com.footprint.backend.repository.TeamRepository;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 스키마 관리(O-1) 규칙을 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 테이블은 Flyway migration이 만들고 Hibernate는 ddl-auto: validate로 확인만 합니다. 이 테스트가 뜬 것 자체가 validate 통과입니다.
// 부분 unique 인덱스(V2)는 Service의 검사를 지나친 저장을 막는 마지막 장치라서, 동료평가 unique 제약 테스트처럼 Repository로 바로 저장해 확인합니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SchemaRuleTests {

    private final TestApi api;
    private final Flyway flyway;
    private final Environment environment;
    private final DataInitializer dataInitializer;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ConfirmationRepository confirmationRepository;
    private final MemberRepository memberRepository;
    private final DeliverableRepository deliverableRepository;
    private final ContributionRepository contributionRepository;
    private final NotificationRepository notificationRepository;
    private final EntityManager entityManager;

    // 시작 데이터의 평가 단계 과제 1조(student01~03) 조원입니다. 이 조에는 완료확인 2건이 이미 들어 있습니다.
    private List<TeamMember> evaluationMembers;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    SchemaRuleTests(MockMvc mockMvc, Flyway flyway, Environment environment, DataInitializer dataInitializer,
                    TeamRepository teamRepository, TeamMemberRepository teamMemberRepository,
                    ConfirmationRepository confirmationRepository, MemberRepository memberRepository,
                    DeliverableRepository deliverableRepository, ContributionRepository contributionRepository,
                    NotificationRepository notificationRepository, EntityManager entityManager) {
        this.api = new TestApi(mockMvc);
        this.flyway = flyway;
        this.environment = environment;
        this.dataInitializer = dataInitializer;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.confirmationRepository = confirmationRepository;
        this.memberRepository = memberRepository;
        this.deliverableRepository = deliverableRepository;
        this.contributionRepository = contributionRepository;
        this.notificationRepository = notificationRepository;
        this.entityManager = entityManager;
    }

    // 평가 단계 과제 1조의 조원을 찾아 둡니다.
    @BeforeEach
    void findSeedTeam() {
        Team team = teamRepository.findByInviteCode("demo-evaluation-1").orElseThrow();
        evaluationMembers = teamMemberRepository.findByTeamIdOrderByIdAsc(team.getId());
    }

    // migration 파일이 모두 적용되었고 남은 것이 없습니다. Hibernate는 테이블을 만들지 않고 확인만 합니다.
    @Test
    void migrationsBuildTheSchemaAndHibernateOnlyValidates() {
        List<String> appliedVersions = Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .map(Object::toString)
                .toList();

        assertThat(appliedVersions).contains("1", "2");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    // 한 조원의 유효한 산출물 확인은 하나입니다. 시작 데이터의 확인(student01 → 최종 보고서)과 같은 확인을 하나 더 저장하면 거절됩니다.
    @Test
    void databaseRejectsSecondActiveDeliverableConfirmation() {
        TeamMember confirmer = evaluationMembers.get(0);
        Confirmation existing = confirmationRepository.findByConfirmerIdOrderByIdDesc(confirmer.getId()).get(0);

        assertThrows(DataIntegrityViolationException.class,
                () -> confirmationRepository.saveAndFlush(new Confirmation(confirmer, existing.getDeliverable())));
    }

    // 취소한 확인은 인덱스에 들어가지 않습니다. 취소한 뒤에는 같은 조원이 같은 산출물을 다시 확인한 새 행이 저장됩니다.
    @Test
    void databaseAllowsNewDeliverableConfirmationAfterCancel() {
        TeamMember confirmer = evaluationMembers.get(0);
        Confirmation existing = confirmationRepository.findByConfirmerIdOrderByIdDesc(confirmer.getId()).get(0);
        existing.cancel();
        confirmationRepository.saveAndFlush(existing);

        assertDoesNotThrow(() -> confirmationRepository.saveAndFlush(new Confirmation(confirmer, existing.getDeliverable())));
    }

    // 기여 항목에도 같은 규칙입니다. 시작 데이터의 확인(student03 → 자료 취합)과 같은 확인을 하나 더 저장하면 거절됩니다.
    @Test
    void databaseRejectsSecondActiveContributionConfirmation() {
        TeamMember confirmer = evaluationMembers.get(2);
        Confirmation existing = confirmationRepository.findByConfirmerIdOrderByIdDesc(confirmer.getId()).get(0);

        assertThrows(DataIntegrityViolationException.class,
                () -> confirmationRepository.saveAndFlush(new Confirmation(confirmer, existing.getContribution())));
    }

    // 조장은 조마다 한 명입니다. 조장이 있는 조에서 다른 조원을 조장으로 저장하면 거절됩니다.
    @Test
    void databaseRejectsSecondLeader() {
        TeamMember member = evaluationMembers.get(1);
        member.becomeLeader();

        assertThrows(DataIntegrityViolationException.class, () -> teamMemberRepository.saveAndFlush(member));
    }

    // 인덱스가 있어도 조장 교체 API는 그대로 됩니다. 이전 조장을 먼저 내리고 새 조장을 올리기 때문입니다(TeamService.changeLeader).
    // 테스트는 요청과 한 트랜잭션이라 바뀐 값이 아직 데이터베이스에 가지 않았을 수 있어서, flush로 직접 보내 인덱스 검사를 받게 합니다.
    @Test
    void leaderChangePassesTheIndex() throws Exception {
        Team team = teamRepository.findByInviteCode("demo-team-1").orElseThrow();
        Long newLeaderId = teamMemberRepository.findByTeamIdOrderByIdAsc(team.getId()).get(1).getId();
        // 테스트가 읽어 둔 조원을 JPA 기억(영속성 컨텍스트)에서 비웁니다. 그래야 실제 서버의 요청처럼 Service가 새 조장을 먼저 읽고,
        // flush가 없으면 새 조장의 UPDATE가 먼저 나가서 이 테스트가 실패합니다.
        entityManager.clear();

        api.put("/teams/" + team.getId() + "/leader", api.login("professor@example.com"),
                        "{\"teamMemberId\": " + newLeaderId + "}")
                .andExpect(status().isOk());

        assertDoesNotThrow(teamMemberRepository::flush);
        assertThat(teamMemberRepository.findByTeamIdAndLeaderTrue(team.getId()).orElseThrow().getId()).isEqualTo(newLeaderId);
    }

    // 서버를 다시 켜도 기록이 남으므로, 시작 데이터는 빈 데이터베이스에서만 들어갑니다. 한 번 더 실행해도 행이 늘지 않습니다.
    @Test
    void dataInitializerRunsOnlyOnEmptyDatabase() {
        long members = memberRepository.count();
        long teamMembers = teamMemberRepository.count();
        long deliverables = deliverableRepository.count();
        long contributions = contributionRepository.count();
        long confirmations = confirmationRepository.count();
        long notifications = notificationRepository.count();

        dataInitializer.run();

        assertThat(memberRepository.count()).isEqualTo(members);
        assertThat(teamMemberRepository.count()).isEqualTo(teamMembers);
        assertThat(deliverableRepository.count()).isEqualTo(deliverables);
        assertThat(contributionRepository.count()).isEqualTo(contributions);
        assertThat(confirmationRepository.count()).isEqualTo(confirmations);
        assertThat(notificationRepository.count()).isEqualTo(notifications);
    }
}
