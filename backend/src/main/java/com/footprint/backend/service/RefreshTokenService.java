package com.footprint.backend.service;

import com.footprint.backend.entity.Member;
import com.footprint.backend.entity.RefreshToken;
import com.footprint.backend.exception.UnauthorizedException;
import com.footprint.backend.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

// refresh token을 만들고, 한 번 쓰고 지우고(교체), 로그아웃할 때 지웁니다.
// 값은 데이터베이스에 저장하지 않고 SHA-256 hash만 저장합니다. 그래서 요청으로 온 값도 hash로 바꿔서 찾습니다.
@Service
public class RefreshTokenService {

    // 재발급이 실패한 이유(cookie 없음, 이미 쓴 값, 로그아웃한 값, 만료)를 구분하지 않고 같은 문장을 줍니다.
    private static final String EXPIRED_MESSAGE = "로그인이 만료되었습니다. 다시 로그인합니다";
    // 난수의 길이입니다. 32바이트(256비트)는 맞혀 볼 수 없는 길이입니다.
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final long validHours;
    // SecureRandom: 맞혀 볼 수 없는 난수를 만드는 Java 기본 클래스입니다. 일반 Random은 다음 값을 예측할 수 있어서 쓰지 않습니다.
    private final SecureRandom secureRandom = new SecureRandom();

    // validHours는 application.yml의 footprint.refresh-token-hours입니다(기본 24).
    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               @Value("${footprint.refresh-token-hours}") long validHours) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.validHours = validHours;
    }

    // 지금 로그인하면 refresh token이 만료될 시각입니다.
    public LocalDateTime expiresAtFromNow() {
        return LocalDateTime.now().plusHours(validHours);
    }

    // 새 refresh token을 만들어 hash를 저장하고, 값을 돌려줍니다. 값은 cookie로만 나가고 어디에도 저장하지 않습니다.
    @Transactional
    public String issue(Member member, LocalDateTime expiresAt) {
        byte[] random = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(random);
        // URL 글자(A-Z, a-z, 0-9, -, _)만 쓰는 Base64입니다. cookie 값에 넣어도 바꿔 적을 글자가 없습니다.
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        refreshTokenRepository.save(new RefreshToken(member, hash(value), expiresAt));
        return value;
    }

    // 한 회원의 만료된 refresh token을 지웁니다. 로그인할 때 불러서 행이 쌓이지 않게 합니다.
    @Transactional
    public void deleteExpired(Member member) {
        refreshTokenRepository.deleteExpiredByMemberId(member.getId(), LocalDateTime.now());
    }

    // refresh token을 한 번 씁니다. 맞으면 그 행을 지우고 돌려줍니다. 돌려준 행의 회원과 만료 시각으로 새 token을 만듭니다.
    // 검사 순서: cookie가 있는가 → 데이터베이스에 있는가 → 만료되지 않았는가 → 지금 지웠는가. 하나라도 아니면 401입니다.
    @Transactional
    public RefreshToken use(String value) {
        if (value == null || value.isBlank()) {
            throw new UnauthorizedException(EXPIRED_MESSAGE);
        }
        String tokenHash = hash(value);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException(EXPIRED_MESSAGE));
        if (refreshToken.isExpired()) {
            throw new UnauthorizedException(EXPIRED_MESSAGE);
        }
        // 찾은 뒤에 다른 요청이 같은 값으로 먼저 지웠을 수 있습니다. 지운 행이 없으면 늦은 쪽이라 401입니다.
        if (refreshTokenRepository.deleteByTokenHash(tokenHash) == 0) {
            throw new UnauthorizedException(EXPIRED_MESSAGE);
        }
        return refreshToken;
    }

    // 로그아웃: 그 refresh token의 행을 지웁니다. 값이 없거나 데이터베이스에 없어도 아무 일 없이 끝납니다.
    @Transactional
    public void delete(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        refreshTokenRepository.deleteByTokenHash(hash(value));
    }

    // 값을 SHA-256 hash로 바꿔 16진수 64글자로 돌려줍니다. 같은 값은 언제나 같은 hash가 됩니다.
    // 비밀번호처럼 BCrypt를 쓰지 않는 이유: 사람이 정한 글자가 아니라 32바이트 난수라서 맞혀 볼 수 없고, hash로 행을 바로 찾아야 하기 때문입니다.
    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // 모든 Java에 SHA-256이 들어 있어서 일어나지 않습니다.
            throw new IllegalStateException(e);
        }
    }
}
