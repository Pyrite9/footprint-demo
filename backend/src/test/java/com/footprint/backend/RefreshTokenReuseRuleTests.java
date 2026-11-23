package com.footprint.backend;

import com.footprint.backend.entity.RefreshToken;
import com.footprint.backend.repository.MemberRepository;
import com.footprint.backend.repository.RefreshTokenRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// refresh token 재사용 감지(이미 쓴 값이 다시 오면 그 묶음을 모두 지움)를 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 이 클래스만 @Transactional을 붙이지 않습니다. 붙이면 MockMvc 요청이 테스트의 트랜잭션 안에서 돌아서 끝까지 commit 되지 않습니다.
// 그러면 401로 rollback 되어 지운 묶음이 되살아나는 잘못을 테스트가 알아채지 못합니다. 여기서는 요청마다 실제로 commit 됩니다.
// 대신 테스트가 만든 refresh_token 행은 되돌려지지 않아서 @AfterEach에서 직접 지웁니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenReuseRuleTests {

    private static final String EMAIL = "student01@example.com";
    private static final String COOKIE_NAME = "refreshToken";
    private static final String EXPIRED_MESSAGE = "로그인이 만료되었습니다. 다시 로그인합니다";
    private static final Pattern COOKIE_VALUE = Pattern.compile("^refreshToken=([^;]*)");

    private final MockMvc mockMvc;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberRepository memberRepository;

    @Autowired
    RefreshTokenReuseRuleTests(MockMvc mockMvc, RefreshTokenRepository refreshTokenRepository, MemberRepository memberRepository) {
        this.mockMvc = mockMvc;
        this.refreshTokenRepository = refreshTokenRepository;
        this.memberRepository = memberRepository;
    }

    // 테스트가 로그인한 회원의 refresh_token 행만 지웁니다. 다른 회원의 행과 다른 테이블은 건드리지 않습니다.
    @AfterEach
    void deleteRefreshTokens() {
        Long memberId = memberRepository.findByEmail(EMAIL).orElseThrow().getId();
        // getMember().getId()는 회원을 데이터베이스에서 더 읽지 않고 id만 꺼냅니다. 그래서 트랜잭션 밖에서도 됩니다.
        List<RefreshToken> rows = refreshTokenRepository.findAll().stream()
                .filter(row -> row.getMember().getId().equals(memberId))
                .toList();
        refreshTokenRepository.deleteAll(rows);
    }

    // 이미 쓴 값을 다시 보내면 401이고, 그 로그인에서 가장 최근에 받은 값도 401입니다. 묶음의 행이 데이터베이스에서 모두 지워집니다.
    @Test
    void reusedTokenEndsWholeFamily() throws Exception {
        String first = loginCookie();
        String familyId = refreshTokenRepository.findByTokenHash(sha256(first)).orElseThrow().getFamilyId();
        String second = refreshCookie(first);
        String latest = refreshCookie(second);

        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(EXPIRED_MESSAGE));

        refresh(latest)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(EXPIRED_MESSAGE));
        assertThat(refreshTokenRepository.findAll().stream().filter(row -> row.getFamilyId().equals(familyId)).count())
                .isZero();
    }

    // 재사용으로 끊기는 것은 그 묶음뿐입니다. 같은 회원의 다른 로그인(다른 브라우저, 다른 묶음)은 그대로 재발급됩니다.
    @Test
    void reuseKeepsOtherFamilies() throws Exception {
        String stolen = loginCookie();
        String otherBrowser = loginCookie();
        refreshCookie(stolen);

        refresh(stolen).andExpect(status().isUnauthorized());

        refresh(otherBrowser).andExpect(status().isOk());
    }

    // 시작 계정으로 로그인하고 refresh token 값(cookie 값)을 돌려줍니다.
    private String loginCookie() throws Exception {
        String setCookie = mockMvc.perform(MockMvcRequestBuilders.post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "pass1234" }
                                """.formatted(EMAIL)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        return cookieValue(setCookie);
    }

    // 재발급에 성공하고 새 refresh token 값을 돌려줍니다.
    private String refreshCookie(String value) throws Exception {
        return cookieValue(refresh(value).andExpect(status().isOk()).andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    // refresh token cookie를 붙여 재발급을 부릅니다.
    private ResultActions refresh(String value) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh").cookie(new Cookie(COOKIE_NAME, value)));
    }

    // Set-Cookie header에서 cookie 값만 꺼냅니다.
    private static String cookieValue(String setCookie) {
        Matcher matcher = COOKIE_VALUE.matcher(setCookie);
        assertThat(matcher.find()).as("Set-Cookie: " + setCookie).isTrue();
        return matcher.group(1);
    }

    // 서버(RefreshTokenService)와 같은 방법으로 SHA-256 hash를 16진수로 만듭니다.
    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
