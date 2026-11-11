package com.footprint.backend;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 개인정보 고지 동의(X-2) 규칙을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 시작 데이터에서 임지아(student10)만 동의 전이고, 나머지 시작 계정은 동의한 상태입니다.
// @Transactional: 테스트가 끝나면 데이터베이스 변경을 되돌립니다. 그래서 테스트끼리 서로 영향을 주지 않습니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PrivacyConsentRuleTests {

    private static final String CONSENT_PATH = "/members/me/privacy-consent";
    private static final String PENDING_EMAIL = "student10@example.com";

    private final MockMvc mockMvc;
    private final TestApi api;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    PrivacyConsentRuleTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
        this.api = new TestApi(mockMvc);
    }

    // 동의하기 전에는 내 정보의 동의 시각이 null입니다. 동의한 시작 계정은 시각이 있습니다.
    @Test
    void consentedAtIsNullBeforeConsent() throws Exception {
        api.get("/members/me", api.login(PENDING_EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privacyConsentedAt").isEmpty());
        api.get("/members/me", api.login("student01@example.com"))
                .andExpect(jsonPath("$.privacyConsentedAt").isNotEmpty());
    }

    // 동의하면 서버가 지금 시각을 저장하고, 내 정보와 같은 모양으로 돌려줍니다. 이후 내 정보에도 같은 시각이 보입니다.
    @Test
    void consentStoresServerTime() throws Exception {
        String token = api.login(PENDING_EMAIL);
        LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        String consentedAt = consent(token, "");

        LocalDateTime after = LocalDateTime.now();
        assertThat(LocalDateTime.parse(consentedAt)).isBetween(before, after);
        api.get("/members/me", token)
                .andExpect(jsonPath("$.email").value(PENDING_EMAIL))
                .andExpect(jsonPath("$.privacyConsentedAt").value(consentedAt));
    }

    // 클라이언트가 body에 시각을 보내도 읽지 않고 서버 시각을 저장합니다.
    @Test
    void clientTimeIsIgnored() throws Exception {
        String token = api.login(PENDING_EMAIL);
        LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        String consentedAt = consent(token, """
                { "privacyConsentedAt": "2000-01-01T00:00:00" }
                """);

        assertThat(LocalDateTime.parse(consentedAt)).isAfterOrEqualTo(before);
    }

    // 이미 동의한 회원이 다시 동의해도 200이고, 처음 동의한 시각을 그대로 둡니다.
    @Test
    void consentAgainKeepsFirstTime() throws Exception {
        String token = api.login(PENDING_EMAIL);
        String first = consent(token, "");

        // 시각을 초 단위로 저장하므로, 1초 넘게 기다려야 덮어썼는지 구분할 수 있습니다.
        Thread.sleep(1100);
        String second = consent(token, "");

        assertThat(second).isEqualTo(first);
    }

    // 한 회원의 동의는 다른 회원의 동의 시각을 바꾸지 않습니다. 주소에 회원 번호가 없어서 본인 것만 기록됩니다.
    @Test
    void consentIsOwnOnly() throws Exception {
        String otherToken = api.login("student09@example.com");
        String otherConsentedAt = JsonPath.read(api.get("/members/me", otherToken)
                .andReturn().getResponse().getContentAsString(), "$.privacyConsentedAt");

        consent(api.login(PENDING_EMAIL), "");

        api.get("/members/me", otherToken).andExpect(jsonPath("$.privacyConsentedAt").value(otherConsentedAt));
    }

    // token이 없으면 401입니다.
    @Test
    void loginIsRequired() throws Exception {
        TestApi.expectError(mockMvc.perform(MockMvcRequestBuilders.post(CONSENT_PATH)), 401, "UNAUTHORIZED");
    }

    // 동의 전 회원의 다른 요청을 서버는 막지 않습니다. 동의 전에 다른 화면으로 가지 못하게 하는 것은 화면이 합니다(docs/worklog.md X-2 결정).
    @Test
    void serverDoesNotBlockBeforeConsent() throws Exception {
        api.get("/assignments", api.login(PENDING_EMAIL)).andExpect(status().isOk());
    }

    // 동의를 보내고 응답의 동의 시각을 돌려줍니다. 시각은 null이 아니어야 합니다.
    private String consent(String token, String body) throws Exception {
        String response = api.post(CONSENT_PATH, token, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(PENDING_EMAIL))
                .andExpect(jsonPath("$.privacyConsentedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.privacyConsentedAt");
    }
}
