package com.footprint.backend.controller;

import com.footprint.backend.dto.GoogleLoginRequest;
import com.footprint.backend.dto.LoginRequest;
import com.footprint.backend.dto.LoginResponse;
import com.footprint.backend.exception.UnauthorizedException;
import com.footprint.backend.service.AuthService;
import com.footprint.backend.service.LoginTokens;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;

// 로그인, access token 재발급, 로그아웃 요청을 받습니다. SecurityConfig에서 /auth/**는 access token 없이 열어 두었습니다.
// access token은 응답 body로, refresh token은 cookie(Set-Cookie header)로 내려줍니다. cookie를 만들고 읽는 일은 이 Controller만 합니다.
@RestController
@RequestMapping("/auth")
public class AuthController {

    // refresh token을 담는 cookie의 이름입니다. 화면의 JS는 이 cookie를 읽지 못합니다(HttpOnly). 브라우저가 요청에 알아서 붙입니다.
    private static final String REFRESH_COOKIE_NAME = "refreshToken";

    private final AuthService authService;
    private final boolean refreshCookieSecure;

    // refreshCookieSecure는 application.yml의 footprint.refresh-cookie-secure입니다. 개발 설정은 false(http), 운영 설정은 true(https만)입니다.
    public AuthController(AuthService authService,
                          @Value("${footprint.refresh-cookie-secure}") boolean refreshCookieSecure) {
        this.authService = authService;
        this.refreshCookieSecure = refreshCookieSecure;
    }

    // 이메일과 비밀번호를 받아 access token을 body로, refresh token을 cookie로 돌려줍니다.
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.login(request.email(), request.password()));
    }

    // 화면의 구글 버튼이 받은 ID token을 받아 token 두 개를 돌려줍니다. 처음 온 구글 계정은 회원으로 저장됩니다.
    @PostMapping("/google")
    public ResponseEntity<LoginResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return withRefreshCookie(authService.loginWithGoogle(request.credential()));
    }

    // cookie의 refresh token으로 새 access token을 돌려줍니다. 새 refresh token도 cookie로 함께 내려줍니다(교체).
    // @CookieValue(required = false): cookie가 없으면 400이 아니라 null을 받습니다. 없을 때의 401은 Service가 정합니다.
    // @RequestHeader("Origin"): 요청을 보낸 화면의 주소입니다. Postman처럼 없으면 null입니다.
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestHeader(value = "Origin", required = false) String origin,
                                                 @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
                                                 HttpServletResponse response) {
        try {
            return withRefreshCookie(authService.refresh(origin, refreshToken));
        } catch (UnauthorizedException e) {
            // 쓸 수 없는 cookie는 브라우저에서도 지웁니다. 401 body는 다른 오류처럼 GlobalExceptionHandler가 만듭니다.
            // 오류 응답을 만들 때도 여기서 붙인 header는 남습니다(Spring은 body만 비우고 header는 그대로 둠).
            response.addHeader(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString());
            throw e;
        }
    }

    // cookie의 refresh token을 서버에서 지우고 브라우저의 cookie도 지웁니다. cookie가 없어도 204입니다.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Origin", required = false) String origin,
                                       @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        authService.logout(origin, refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    // access token은 body에, refresh token은 Set-Cookie header에 담은 200 응답을 만듭니다.
    private ResponseEntity<LoginResponse> withRefreshCookie(LoginTokens tokens) {
        // Max-Age는 만료까지 남은 초입니다. 재발급해도 만료 시각이 그대로라 Max-Age는 점점 줄어듭니다.
        long maxAgeSeconds = Math.max(0, Duration.between(LocalDateTime.now(), tokens.refreshTokenExpiresAt()).toSeconds());
        ResponseCookie cookie = refreshCookie(tokens.refreshToken(), maxAgeSeconds);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new LoginResponse(tokens.accessToken()));
    }

    // 값이 빈 Max-Age=0 cookie입니다. 브라우저는 이 header를 받으면 같은 이름의 cookie를 지웁니다.
    private ResponseCookie clearRefreshCookie() {
        return refreshCookie("", 0);
    }

    // refresh token cookie의 속성을 한 곳에서 정합니다. 지울 때도 속성(Path 등)이 같아야 브라우저가 같은 cookie로 봅니다.
    private ResponseCookie refreshCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                // HttpOnly: 화면의 JS가 읽지 못합니다. 화면에 끼어든 스크립트가 값을 훔쳐 가지 못하게 합니다.
                .httpOnly(true)
                // Secure: true면 https 요청에만 실립니다. 개발과 실행 묶음은 http라 false입니다.
                .secure(refreshCookieSecure)
                // SameSite=Strict: 다른 사이트에서 시작한 요청에는 실리지 않습니다. CSRF를 막는 첫째 장치입니다.
                .sameSite("Strict")
                // Path=/: 화면은 /api/auth/refresh, 가짜 API와 Postman은 /auth/refresh로 불러서 한 경로로 좁히지 않습니다.
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
