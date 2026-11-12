package com.footprint.backend;

import com.footprint.backend.entity.RefreshToken;
import com.footprint.backend.repository.RefreshTokenRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.Cookie;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// access token 재발급과 로그아웃(refresh token) 규칙을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// refresh token은 cookie로 오가므로 응답의 Set-Cookie header를 글자 그대로 읽어 확인합니다.
// @Transactional: 테스트가 끝나면 데이터베이스 변경을 되돌립니다. MockMvc 요청도 같은 트랜잭션 안에서 돕니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RefreshTokenRuleTests {

    private static final String EMAIL = "student01@example.com";
    private static final String COOKIE_NAME = "refreshToken";
    private static final String EXPIRED_MESSAGE = "로그인이 만료되었습니다. 다시 로그인합니다";
    // 기본 설정(footprint.refresh-token-hours: 24)의 Max-Age입니다.
    private static final long DAY_SECONDS = 24 * 60 * 60;
    private static final Pattern COOKIE_VALUE = Pattern.compile("^refreshToken=([^;]*)");
    private static final Pattern MAX_AGE = Pattern.compile("Max-Age=(\\d+)");

    private final MockMvc mockMvc;
    private final TestApi api;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EntityManager entityManager;

    @Autowired
    RefreshTokenRuleTests(MockMvc mockMvc, RefreshTokenRepository refreshTokenRepository, EntityManager entityManager) {
        this.mockMvc = mockMvc;
        this.api = new TestApi(mockMvc);
        this.refreshTokenRepository = refreshTokenRepository;
        this.entityManager = entityManager;
    }

    // 로그인 응답: body는 accessToken 하나이고, refresh token은 HttpOnly, SameSite=Strict, Path=/ cookie로 옵니다.
    // 개발 설정이라 Secure는 없고, Max-Age는 24시간입니다.
    @Test
    void loginSetsRefreshCookie() throws Exception {
        MockHttpServletResponse response = login()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse();

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookieValue(setCookie)).hasSize(43);
        assertThat(setCookie).contains("HttpOnly", "SameSite=Strict", "Path=/").doesNotContain("Secure");
        assertThat(maxAge(setCookie)).isBetween(DAY_SECONDS - 5, DAY_SECONDS);
    }

    // 데이터베이스에는 값이 아니라 값의 SHA-256 hash가 들어갑니다.
    @Test
    void databaseStoresHashNotValue() throws Exception {
        String value = cookieValue(login().andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE));

        assertThat(refreshTokenRepository.findByTokenHash(value)).isEmpty();
        RefreshToken saved = refreshTokenRepository.findByTokenHash(sha256(value)).orElseThrow();
        assertThat(saved.getMember().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getTokenHash()).hasSize(64).isNotEqualTo(value);
    }

    // 재발급하면 새 access token과 새 refresh token을 줍니다. 쓴 refresh token은 다시 쓸 수 없습니다(교체).
    @Test
    void refreshRotatesToken() throws Exception {
        String first = loginCookie();

        MockHttpServletResponse response = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse();
        String second = cookieValue(response.getHeader(HttpHeaders.SET_COOKIE));
        assertThat(second).isNotEqualTo(first);

        // 새 access token으로 API를 부를 수 있습니다.
        String accessToken = com.jayway.jsonpath.JsonPath.read(response.getContentAsString(), "$.accessToken");
        api.get("/members/me", accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));

        TestApi.expectError(refresh(first), 401, "UNAUTHORIZED");
        refresh(second).andExpect(status().isOk());
    }

    // 재발급해도 만료 시각은 처음 로그인의 것을 그대로 물려받습니다(절대 만료).
    @Test
    void refreshKeepsAbsoluteExpiry() throws Exception {
        String first = loginCookie();
        LocalDateTime firstExpiresAt = refreshTokenRepository.findByTokenHash(sha256(first)).orElseThrow().getExpiresAt();

        String second = cookieValue(refresh(first).andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE));

        LocalDateTime secondExpiresAt = refreshTokenRepository.findByTokenHash(sha256(second)).orElseThrow().getExpiresAt();
        assertThat(secondExpiresAt).isEqualTo(firstExpiresAt);
    }

    // 만료 시각이 지난 refresh token은 401이고 같은 문장입니다.
    @Test
    void expiredTokenIsRejected() throws Exception {
        String value = loginCookie();
        // 만료 시각을 1초 전으로 당깁니다. Entity에 고치는 메서드가 없어서 JPQL로 직접 바꿉니다.
        entityManager.createQuery("update RefreshToken r set r.expiresAt = :past where r.tokenHash = :tokenHash")
                .setParameter("past", LocalDateTime.now().minusSeconds(1))
                .setParameter("tokenHash", sha256(value))
                .executeUpdate();
        // 로그인에서 저장한 Entity가 이 테스트의 트랜잭션 안에 바뀌기 전 값으로 남아 있습니다. 비워서 데이터베이스에서 다시 읽게 합니다.
        entityManager.clear();

        refresh(value)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(EXPIRED_MESSAGE));
    }

    // cookie가 없으면 401입니다.
    @Test
    void missingCookieIsRejected() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(EXPIRED_MESSAGE));
    }

    // 401이면 브라우저의 cookie도 지우도록 Max-Age=0 cookie를 내려줍니다. 모르는 값도 같습니다.
    @Test
    void unauthorizedClearsCookie() throws Exception {
        String setCookie = refresh("not-a-real-token")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(cookieValue(setCookie)).isEmpty();
        assertThat(setCookie).contains("Max-Age=0", "Path=/", "HttpOnly", "SameSite=Strict");
    }

    // 로그아웃하면 그 refresh token은 지워져서 재발급이 401입니다. 로그아웃 응답도 cookie를 지웁니다.
    @Test
    void logoutDeletesToken() throws Exception {
        String value = loginCookie();

        String setCookie = logout(value)
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).contains("Max-Age=0");
        assertThat(refreshTokenRepository.findByTokenHash(sha256(value))).isEmpty();
        TestApi.expectError(refresh(value), 401, "UNAUTHORIZED");
    }

    // 로그아웃은 cookie가 없거나 모르는 값이어도 204입니다. 여러 번 눌러도 결과가 같습니다.
    @Test
    void logoutWithoutCookieIsNoContent() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/logout"))
                .andExpect(status().isNoContent());
        logout("not-a-real-token").andExpect(status().isNoContent());
    }

    // Origin이 허용한 화면 주소(cors.allowed-origins, 기본 http://localhost:5173)가 아니면 403입니다. 재발급과 로그아웃 모두입니다.
    // MockMvc의 요청 주소는 http://localhost라서 Origin "http://localhost"는 같은 주소의 요청(CORS 아님)으로 Controller까지 갑니다.
    // 화면을 127.0.0.1:5173으로 열어 proxy로 부른 경우가 이것과 같습니다.
    @Test
    void disallowedOriginIsForbidden() throws Exception {
        String value = loginCookie();

        mockMvc.perform(withCookie(MockMvcRequestBuilders.post("/auth/refresh"), value).header(HttpHeaders.ORIGIN, "http://localhost"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("허용하지 않은 주소에서 온 요청입니다"));
        mockMvc.perform(withCookie(MockMvcRequestBuilders.post("/auth/logout"), value).header(HttpHeaders.ORIGIN, "http://localhost"))
                .andExpect(status().isForbidden());

        // 403이면 refresh token을 쓰지 않아서, 허용한 주소에서는 그대로 재발급됩니다.
        mockMvc.perform(withCookie(MockMvcRequestBuilders.post("/auth/refresh"), value).header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk());
    }

    // 다른 사이트의 Origin은 Controller에 닿기 전에 Spring의 CORS 검사가 403으로 막습니다(body는 JSON이 아님).
    @Test
    void crossSiteOriginIsBlockedByCors() throws Exception {
        String value = loginCookie();

        mockMvc.perform(withCookie(MockMvcRequestBuilders.post("/auth/refresh"), value).header(HttpHeaders.ORIGIN, "http://evil.example"))
                .andExpect(status().isForbidden());
        assertThat(refreshTokenRepository.findByTokenHash(sha256(value))).isPresent();
    }

    // 시작 계정으로 로그인합니다.
    private ResultActions login() throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "email": "%s", "password": "pass1234" }
                        """.formatted(EMAIL)));
    }

    // 로그인하고 refresh token 값(cookie 값)을 돌려줍니다.
    private String loginCookie() throws Exception {
        return cookieValue(login().andExpect(status().isOk()).andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    // refresh token cookie를 붙여 재발급을 부릅니다. Origin은 붙이지 않습니다(Postman과 같음).
    private ResultActions refresh(String value) throws Exception {
        return mockMvc.perform(withCookie(MockMvcRequestBuilders.post("/auth/refresh"), value));
    }

    // refresh token cookie를 붙여 로그아웃을 부릅니다.
    private ResultActions logout(String value) throws Exception {
        return mockMvc.perform(withCookie(MockMvcRequestBuilders.post("/auth/logout"), value));
    }

    // 요청에 refresh token cookie를 붙입니다.
    private MockHttpServletRequestBuilder withCookie(MockHttpServletRequestBuilder request, String value) {
        return request.cookie(new Cookie(COOKIE_NAME, value));
    }

    // Set-Cookie header에서 cookie 값만 꺼냅니다.
    private static String cookieValue(String setCookie) {
        Matcher matcher = COOKIE_VALUE.matcher(setCookie);
        assertThat(matcher.find()).as("Set-Cookie: " + setCookie).isTrue();
        return matcher.group(1);
    }

    // Set-Cookie header에서 Max-Age(초)를 꺼냅니다.
    private static long maxAge(String setCookie) {
        Matcher matcher = MAX_AGE.matcher(setCookie);
        assertThat(matcher.find()).as("Set-Cookie: " + setCookie).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    // 서버(RefreshTokenService)와 같은 방법으로 SHA-256 hash를 16진수로 만듭니다.
    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
