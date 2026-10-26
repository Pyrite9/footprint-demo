package com.footprint.backend.security;

import com.footprint.backend.entity.Member;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

// 로그인한 회원에게 줄 JWT를 만들고, 요청에 담겨 온 JWT를 확인합니다.
@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final long expirationMinutes;

    // @Value는 application.yml의 값을 생성자 인자로 넣어 줍니다.
    public JwtProvider(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.expiration-minutes}") long expirationMinutes) {
        // HS256 서명 키는 32바이트 이상이어야 합니다. 짧으면 서버가 시작할 때 여기서 WeakKeyException이 납니다.
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    // 회원 id와 회원 유형을 담은 token을 만듭니다.
    public String createToken(Member member) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMinutes * 60 * 1000);

        return Jwts.builder()
                // sub(subject)는 "이 token의 주인"입니다. 문자열만 들어가므로 id를 문자열로 바꿉니다.
                .subject(String.valueOf(member.getId()))
                // 참고용입니다. 권한 검사는 Service가 DB에 저장된 회원 유형으로 합니다.
                .claim("memberType", member.getMemberType().name())
                .issuedAt(now)
                .expiration(expiration)
                // 알고리즘을 적지 않으면 jjwt가 키 길이를 보고 HS384 등으로 바꿀 수 있어서 HS256으로 고정합니다.
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    // token의 서명과 만료 시각을 확인하고 회원 id를 꺼냅니다. 서명이 틀렸거나 만료됐으면 JwtException을 던집니다.
    public Long getMemberId(String token) {
        String subject = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return Long.valueOf(subject);
    }
}
