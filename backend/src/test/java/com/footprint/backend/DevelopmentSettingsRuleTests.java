package com.footprint.backend;

import com.footprint.backend.config.ProductionSettingsChecker;
import com.footprint.backend.repository.MemberRepository;
import com.footprint.backend.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 환경 변수 없이 켠 개발 설정(O-2 전과 같은 동작)을 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 다른 규칙 테스트와 설정이 같아서 같은 Spring context를 씁니다. prod 프로필은 켜지 않습니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DevelopmentSettingsRuleTests {

    private final MockMvc mockMvc;
    private final TestApi api;
    private final Environment environment;
    private final MemberRepository memberRepository;
    private final TeamRepository teamRepository;

    @Autowired
    DevelopmentSettingsRuleTests(MockMvc mockMvc, Environment environment, MemberRepository memberRepository,
                                 TeamRepository teamRepository) {
        this.mockMvc = mockMvc;
        this.api = new TestApi(mockMvc);
        this.environment = environment;
        this.memberRepository = memberRepository;
        this.teamRepository = teamRepository;
    }

    // 운영 설정이 아니고, 운영 설정에서 끄는 두 기능이 켜져 있습니다.
    @Test
    void productionProfileIsOff() {
        assertThat(environment.getActiveProfiles()).doesNotContain("prod");
        assertThat(environment.getProperty("footprint.seed-data")).isEqualTo("true");
        assertThat(environment.getProperty("footprint.password-login")).isEqualTo("true");
    }

    // 시작 데이터가 들어가 있습니다.
    @Test
    void seedDataIsInserted() {
        assertThat(memberRepository.findByEmail("professor@example.com")).isPresent();
        assertThat(teamRepository.findByInviteCode("demo-team-1")).isPresent();
    }

    // 시작 계정으로 이메일·비밀번호 로그인이 됩니다(TestApi.login이 200을 확인합니다).
    @Test
    void passwordLoginWorks() throws Exception {
        assertThat(api.login("student01@example.com")).isNotBlank();
    }

    // CORS 기본값은 개발 화면 주소 http://localhost:5173 하나입니다.
    @Test
    void corsAllowsOnlyDevelopmentScreen() throws Exception {
        preflight("http://localhost:5173")
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        preflight("https://footprint.example.com")
                .andExpect(status().isForbidden());
    }

    // 비밀값 검사는 prod 프로필이 아니면 아무것도 하지 않습니다. 기본 비밀값이나 빈 값이어도 그대로 켜집니다.
    @Test
    void checkerDoesNothingWithoutProductionProfile() {
        MockEnvironment developmentEnvironment = new MockEnvironment()
                .withProperty("jwt.secret", "footprint-demo-dev-secret-key-change-me-0000")
                .withProperty("google.professor-emails", "");

        assertDoesNotThrow(() -> new ProductionSettingsChecker().check(developmentEnvironment));
    }

    // 화면이 보내는 것과 같은 CORS 사전 요청(OPTIONS)을 보냅니다.
    private ResultActions preflight(String origin) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.options("/assignments")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "GET"));
    }
}
