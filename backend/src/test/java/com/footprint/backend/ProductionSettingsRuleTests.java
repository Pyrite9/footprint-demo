package com.footprint.backend;

import com.footprint.backend.config.ProductionSettingsChecker;
import com.footprint.backend.entity.Member;
import com.footprint.backend.entity.MemberType;
import com.footprint.backend.repository.AssignmentRepository;
import com.footprint.backend.repository.MemberRepository;
import com.footprint.backend.security.GoogleTokenVerifier;
import com.footprint.backend.security.GoogleTokenVerifier.GoogleAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 운영 설정(O-2) 규칙을 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 운영 설정에서는 시작 데이터를 넣지 않고, 이메일·비밀번호 로그인이 꺼지고, 구글 로그인은 그대로입니다.
// 비밀값이 공개된 개발용 기본값이면 서버가 켜지지 않습니다(BackendApplication.main → config/ProductionSettingsChecker).
// @ActiveProfiles: test 다음에 prod를 켭니다. 뒤에 적은 프로필의 설정 파일이 앞의 것을 덮어씁니다.
// 스키마는 footprint_prod_test입니다. 다른 테스트의 footprint_test를 같이 쓰면, 이 context가 뜰 때 그 스키마를 비우고(TestFlywayConfig)
// 시작 데이터 없이 다시 만들어서 다른 테스트가 찾는 시작 데이터가 사라집니다. 따로 두면 서로 비우지 않습니다.
// @SpringBootTest는 main을 거치지 않아서 비밀값 검사가 돌지 않습니다. 그래서 테스트 DB·MinIO의 공개 비밀번호로도 이 context가 뜹니다.
// 비밀값 검사는 아래에서 main을 직접 불러 따로 확인합니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "prod"})
@Transactional
@TestPropertySource(properties = {
        "spring.flyway.schemas=footprint_prod_test",
        "spring.flyway.default-schema=footprint_prod_test",
        "spring.jpa.properties.hibernate.default_schema=footprint_prod_test",
        "cors.allowed-origins=https://footprint.example.com, https://second.example.com",
        "google.allowed-domains=example.com"
})
class ProductionSettingsRuleTests {

    // 진짜 구글 ID token은 테스트에서 만들 수 없어서, 검증기를 Mockito 대역으로 바꿉니다.
    @MockitoBean
    private GoogleTokenVerifier googleTokenVerifier;

    private final MockMvc mockMvc;
    private final MemberRepository memberRepository;
    private final AssignmentRepository assignmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    ProductionSettingsRuleTests(MockMvc mockMvc, MemberRepository memberRepository,
                                AssignmentRepository assignmentRepository, PasswordEncoder passwordEncoder) {
        this.mockMvc = mockMvc;
        this.memberRepository = memberRepository;
        this.assignmentRepository = assignmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 빈 데이터베이스로 켜도 시작 계정과 데모 과제가 들어가지 않습니다.
    @Test
    void seedDataIsNotInserted() {
        assertThat(memberRepository.count()).isZero();
        assertThat(assignmentRepository.count()).isZero();
    }

    // 비밀번호가 있는 회원이 맞는 비밀번호를 보내도 404 NOT_FOUND입니다. 401이 아니라서 "기능이 꺼짐"과 "비밀번호 틀림"이 구분됩니다.
    @Test
    void passwordLoginIsOff() throws Exception {
        memberRepository.save(new Member("password@example.com", passwordEncoder.encode("pass1234"), "비밀번호 회원", MemberType.STUDENT));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "password@example.com", "password": "pass1234" }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("이메일·비밀번호 로그인은 사용하지 않습니다. 구글 로그인을 사용합니다"));
    }

    // 구글 로그인은 운영 설정에서도 그대로 token을 줍니다.
    @Test
    void googleLoginStillWorks() throws Exception {
        when(googleTokenVerifier.verify("prod-credential"))
                .thenReturn(new GoogleAccount("newcomer@example.com", "새 학생", "example.com"));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "credential": "prod-credential" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
        assertThat(memberRepository.findByEmail("newcomer@example.com")).isPresent();
    }

    // CORS는 cors.allowed-origins에 적은 주소만 받습니다. 여러 주소를 쉼표로 적을 수 있고, 적지 않은 개발 화면 주소는 403입니다.
    @Test
    void corsUsesConfiguredOrigins() throws Exception {
        preflight("https://footprint.example.com")
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://footprint.example.com"));
        preflight("https://second.example.com")
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://second.example.com"));
        preflight("http://localhost:5173")
                .andExpect(status().isForbidden());
    }

    // 운영 설정을 기본 비밀값 그대로 켜면 main이 데이터베이스에 접속하기 전에 멈춥니다. 오류에는 환경 변수 이름만 있고 값은 없습니다.
    // 이 테스트는 JWT_SECRET, POSTGRES_PASSWORD, MINIO_ROOT_PASSWORD 환경 변수가 없는 컴퓨터(CI, 조원 PC)에서 돈다는 전제입니다.
    @Test
    void mainStopsWithPublicDefaults() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> BackendApplication.main(new String[]{"--spring.profiles.active=prod", "--google.professor-emails="}));

        assertThat(checkerMessage(e))
                .contains("JWT_SECRET", "POSTGRES_PASSWORD", "MINIO_ROOT_PASSWORD", "PROFESSOR_EMAILS")
                .doesNotContain("change-me");
    }

    // 바꾼 값은 오류에서 빠지고, 아직 기본값인 것만 남습니다.
    @Test
    void mainNamesOnlyRemainingDefaults() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> BackendApplication.main(new String[]{
                        "--spring.profiles.active=prod",
                        "--jwt.secret=prod-test-only-secret-key-32-bytes-0000",
                        "--google.professor-emails=professor@example.com"}));

        assertThat(checkerMessage(e))
                .startsWith("운영 설정(prod)으로 켤 수 없습니다. 바꿔야 하는 환경 변수: POSTGRES_PASSWORD, MINIO_ROOT_PASSWORD.");
    }

    // 모든 값을 바꾸면 검사를 통과합니다. main으로 부르면 실제 서버가 켜져서, 검사 메서드를 직접 부릅니다.
    @Test
    void checkerPassesWhenAllValuesChanged() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("jwt.secret", "prod-test-only-secret-key-32-bytes-0000")
                .withProperty("spring.datasource.password", "prod-test-db-password")
                .withProperty("storage.secret-key", "prod-test-minio-password")
                .withProperty("google.professor-emails", "professor@example.com");
        environment.setActiveProfiles("prod");

        assertDoesNotThrow(() -> new ProductionSettingsChecker().check(environment));
    }

    // 빈 비밀값과 쉼표만 있는 교수 목록도 막습니다.
    @Test
    void checkerRejectsBlankValues() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("jwt.secret", " ")
                .withProperty("spring.datasource.password", "")
                .withProperty("storage.secret-key", "prod-test-minio-password")
                .withProperty("google.professor-emails", " , ");
        environment.setActiveProfiles("prod");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new ProductionSettingsChecker().check(environment));
        assertThat(e.getMessage()).contains("바꿔야 하는 환경 변수: JWT_SECRET, POSTGRES_PASSWORD, PROFESSOR_EMAILS.");
    }

    // SpringApplication.run은 시작 중에 난 예외를 한 번 더 감싸서 던집니다. 검사가 던진 맨 안쪽 예외의 메시지를 꺼냅니다.
    private String checkerMessage(Exception e) {
        return NestedExceptionUtils.getMostSpecificCause(e).getMessage();
    }

    // 화면이 보내는 것과 같은 CORS 사전 요청(OPTIONS)을 보냅니다.
    private ResultActions preflight(String origin) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.options("/assignments")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "GET"));
    }
}
