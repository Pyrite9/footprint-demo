package com.footprint.backend.service;

import com.footprint.backend.entity.Member;
import com.footprint.backend.entity.RefreshToken;
import com.footprint.backend.exception.UnauthorizedException;
import com.footprint.backend.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// refresh token을 만들고, 한 번 쓰고(교체), 로그아웃할 때 지우고, 만료된 것을 정리합니다.
// 한 번의 로그인에서 재발급으로 이어진 값들은 같은 묶음(family)입니다. 이미 쓴 값이 다시 오면 그 묶음을 모두 지웁니다(재사용 감지).
// 값은 데이터베이스에 저장하지 않고 SHA-256 hash만 저장합니다. 그래서 요청으로 온 값도 hash로 바꿔서 찾습니다.
@Service
public class RefreshTokenService {

    // 재사용을 감지했을 때 서버 로그에 경고를 남길 때 씁니다.
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

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

    // 새 묶음 id를 만듭니다. 로그인할 때마다 부릅니다. UUID는 36글자이고 겹치지 않는다고 봐도 되는 무작위 값입니다.
    public String newFamilyId() {
        return UUID.randomUUID().toString();
    }

    // 새 refresh token을 만들어 hash를 저장하고, 값을 돌려줍니다. 값은 cookie로만 나가고 어디에도 저장하지 않습니다.
    // familyId는 로그인이면 newFamilyId()로 만든 새 값, 재발급이면 쓴 행의 값입니다.
    @Transactional
    public String issue(Member member, LocalDateTime expiresAt, String familyId) {
        byte[] random = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(random);
        // URL 글자(A-Z, a-z, 0-9, -, _)만 쓰는 Base64입니다. cookie 값에 넣어도 바꿔 적을 글자가 없습니다.
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        refreshTokenRepository.save(new RefreshToken(member, hash(value), familyId, expiresAt));
        return value;
    }

    // refresh token을 한 번 씁니다. 맞으면 그 행에 쓴 시각을 적고 돌려줍니다. 돌려준 행의 회원, 만료 시각, 묶음 id로 새 token을 만듭니다.
    // 검사 순서: cookie가 있는가 → 데이터베이스에 있는가 → 이미 쓴 값인가 → 만료되지 않았는가 → 지금 쓴 시각을 적었는가. 하나라도 아니면 401입니다.
    // 이미 쓴 값이면(재사용) 401을 주기 전에 그 묶음의 행을 모두 지웁니다.
    // noRollbackFor: @Transactional은 RuntimeException이 나가면 이 메서드에서 한 일을 모두 되돌립니다(rollback).
    // 그러면 재사용 때 지운 묶음도 되살아납니다. UnauthorizedException일 때는 되돌리지 않고 commit 하게 해서 지운 것을 남깁니다.
    // 이 메서드는 AuthService.refresh의 트랜잭션에 참여하므로 AuthService.refresh에도 같은 설정이 있어야 합니다.
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public RefreshToken use(String value) {
        if (value == null || value.isBlank()) {
            throw new UnauthorizedException(EXPIRED_MESSAGE);
        }
        String tokenHash = hash(value);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException(EXPIRED_MESSAGE));
        if (refreshToken.isUsed()) {
            endFamily(refreshToken);
            throw new UnauthorizedException(EXPIRED_MESSAGE);
        }
        if (refreshToken.isExpired()) {
            throw new UnauthorizedException(EXPIRED_MESSAGE);
        }
        // 찾은 뒤에 다른 요청이 같은 값으로 먼저 썼을 수 있습니다. 고친 행이 없으면 늦은 쪽이라 재사용과 똑같이 처리합니다.
        if (refreshTokenRepository.markUsed(tokenHash, LocalDateTime.now()) == 0) {
            endFamily(refreshToken);
            throw new UnauthorizedException(EXPIRED_MESSAGE);
        }
        return refreshToken;
    }

    // 로그아웃: 그 refresh token이 속한 묶음의 행을 모두 지웁니다. 값이 없거나 데이터베이스에 없어도 아무 일 없이 끝납니다.
    // 같은 회원의 다른 묶음(다른 브라우저의 로그인)은 지우지 않습니다.
    @Transactional
    public void delete(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(value))
                .ifPresent(refreshToken -> refreshTokenRepository.deleteByFamilyId(refreshToken.getFamilyId()));
    }

    // 모든 회원의 만료된 refresh token을 지웁니다. 서버가 정해진 간격마다 불러서 행이 쌓이지 않게 합니다.
    // @Scheduled: config/SchedulingConfig의 @EnableScheduling이 켜 둔 기능으로, Spring이 이 메서드를 알아서 부릅니다.
    // fixedDelayString: 앞의 실행이 끝난 뒤 다음 실행까지 기다리는 간격입니다. application.yml의 footprint.refresh-token-cleanup-minutes입니다.
    // initialDelayString: 서버가 켜진 뒤 첫 실행까지 기다리는 시간입니다. 간격과 같게 두어 켜자마자 돌지 않게 합니다.
    // timeUnit: 위 두 숫자의 단위입니다. 분입니다.
    @Scheduled(fixedDelayString = "${footprint.refresh-token-cleanup-minutes}",
            initialDelayString = "${footprint.refresh-token-cleanup-minutes}",
            timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void deleteExpired() {
        refreshTokenRepository.deleteExpired(LocalDateTime.now());
    }

    // 이미 쓴 값이 다시 왔을 때 그 묶음의 행을 모두 지우고 경고 로그를 남깁니다.
    // 값을 두 곳(훔친 쪽과 원래 브라우저)이 갖고 있다는 뜻인데 어느 쪽이 진짜인지 몰라서 둘 다 끊습니다.
    private void endFamily(RefreshToken refreshToken) {
        refreshTokenRepository.deleteByFamilyId(refreshToken.getFamilyId());
        // 개인정보와 비밀값을 로그에 찍지 않는 규칙이라 회원 id만 남깁니다. token 값, hash, 이메일, 이름은 찍지 않습니다.
        log.warn("이미 쓴 refresh token이 다시 와서 그 로그인의 refresh token을 모두 지웠습니다. 회원 id: {}",
                refreshToken.getMember().getId());
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
