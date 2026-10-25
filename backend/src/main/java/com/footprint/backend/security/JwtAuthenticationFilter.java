package com.footprint.backend.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// 요청 header의 token을 확인하고, 맞으면 "이 요청을 보낸 회원"을 SecurityContext에 적어 둡니다.
// 요청을 막지는 않습니다. 막는 일은 SecurityConfig의 authorizeHttpRequests가 합니다.
// @Component를 붙이지 않습니다. 붙이면 Spring Boot가 Security 밖의 일반 filter로 한 번 더 등록합니다. 등록이 두 군데가 되지 않도록 SecurityConfig에서 new로 만듭니다.
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    // 요청마다 한 번 실행됩니다.
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // header 모양: "Authorization: Bearer eyJhbGci..."
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Long memberId = jwtProvider.getMemberId(token);
                // 첫 번째 인자(principal)가 Controller의 @AuthenticationPrincipal로 들어갑니다.
                // 권한 목록(세 번째 인자)을 받는 생성자를 써야 "로그인한 요청"으로 표시됩니다. 권한은 Service에서 검사하므로 빈 목록입니다.
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(memberId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException e) {
                // 서명이 틀렸거나 만료된 token입니다. 아무것도 적지 않고 넘기면, token이 필요한 주소에서 401이 됩니다.
            }
        }

        // token이 있든 없든 반드시 다음 단계로 넘깁니다. 빠뜨리면 요청이 응답 없이 멈춥니다.
        filterChain.doFilter(request, response);
    }
}
