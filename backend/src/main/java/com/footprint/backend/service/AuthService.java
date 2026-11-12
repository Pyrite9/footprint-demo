package com.footprint.backend.service;

import com.footprint.backend.entity.Member;
import com.footprint.backend.entity.MemberType;
import com.footprint.backend.entity.RefreshToken;
import com.footprint.backend.exception.ForbiddenException;
import com.footprint.backend.exception.NotFoundException;
import com.footprint.backend.exception.UnauthorizedException;
import com.footprint.backend.repository.MemberRepository;
import com.footprint.backend.repository.TeamMemberRepository;
import com.footprint.backend.security.GoogleTokenVerifier;
import com.footprint.backend.security.GoogleTokenVerifier.GoogleAccount;
import com.footprint.backend.security.JwtProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// 로그인을 확인하고 token을 발급합니다. 12.11 데모는 구글 로그인이 기본이고, 이메일·비밀번호 로그인은 시작 계정용(개발·백업)입니다.
// 운영 설정(prod)에서는 이메일·비밀번호 로그인이 꺼집니다(login 메서드 첫 줄).
// 로그인하면 access token(JWT, 10분)과 refresh token(24시간)을 함께 줍니다. refresh token으로 access token을 다시 받고(refresh), 로그아웃하면 지웁니다.
@Service
public class AuthService {

    // 이메일이 없을 때와 비밀번호가 틀렸을 때 같은 메시지를 줍니다. 어느 이메일이 가입돼 있는지 알려 주지 않기 위해서입니다.
    private static final String LOGIN_FAILED_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다";

    private final MemberRepository memberRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final RefreshTokenService refreshTokenService;
    private final List<String> allowedDomains;
    private final List<String> professorEmails;
    private final boolean passwordLoginEnabled;
    private final List<String> allowedOrigins;

    // allowedDomains와 professorEmails는 application.yml의 쉼표로 구분한 값을 Spring이 목록으로 바꿔 넣어 줍니다.
    // passwordLoginEnabled는 application.yml의 footprint.password-login입니다. 운영 설정(application-prod.yml)에서는 false입니다.
    // allowedOrigins는 CORS와 같은 cors.allowed-origins입니다. refresh token을 쓰는 요청의 Origin 검사에 씁니다.
    public AuthService(MemberRepository memberRepository, TeamMemberRepository teamMemberRepository,
                       PasswordEncoder passwordEncoder, JwtProvider jwtProvider, GoogleTokenVerifier googleTokenVerifier,
                       RefreshTokenService refreshTokenService,
                       @Value("${google.allowed-domains}") List<String> allowedDomains,
                       @Value("${google.professor-emails}") List<String> professorEmails,
                       @Value("${footprint.password-login}") boolean passwordLoginEnabled,
                       @Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        this.memberRepository = memberRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.googleTokenVerifier = googleTokenVerifier;
        this.refreshTokenService = refreshTokenService;
        this.allowedDomains = allowedDomains;
        // 대소문자를 가리지 않고 비교하려고 소문자로 바꿔 둡니다.
        this.professorEmails = professorEmails.stream().map(String::toLowerCase).toList();
        this.passwordLoginEnabled = passwordLoginEnabled;
        this.allowedOrigins = allowedOrigins;
    }

    // 이메일과 비밀번호로 로그인합니다. 성공하면 token 두 개를 돌려줍니다. 실패하면 UnauthorizedException을 던집니다.
    // refresh token을 저장하므로 @Transactional입니다.
    @Transactional
    public LoginTokens login(String email, String password) {
        // 운영 설정(application-prod.yml의 footprint.password-login: false)에서는 여기서 꺼집니다.
        // 이 서버에 없는 기능이라 "없는 주소"와 같은 404로 답합니다. 401로 답하면 비밀번호가 틀린 것으로 읽힙니다.
        if (!passwordLoginEnabled) {
            throw new NotFoundException("이메일·비밀번호 로그인은 사용하지 않습니다. 구글 로그인을 사용합니다");
        }

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException(LOGIN_FAILED_MESSAGE));

        // 구글로 만든 회원은 비밀번호가 없어서 이 방법으로 로그인할 수 없습니다.
        // matches(입력한 비밀번호, 저장된 BCrypt 값) 순서입니다. BCrypt는 되돌릴 수 없어서 입력값을 같은 방식으로 바꿔 비교합니다.
        if (member.getPassword() == null || !passwordEncoder.matches(password, member.getPassword())) {
            throw new UnauthorizedException(LOGIN_FAILED_MESSAGE);
        }
        return createLoginTokens(member);
    }

    // 구글 계정으로 로그인합니다. 처음 온 계정이면 회원으로 저장합니다(M-1-1 회원 등록). token 두 개를 돌려줍니다.
    // 이미 있는 회원이 교수 목록에 있는 학생이면 교수로 올립니다(X-1). 회원 유형을 바꾸고 refresh token을 저장해서 @Transactional입니다.
    @Transactional
    public LoginTokens loginWithGoogle(String credential) {
        GoogleAccount account = googleTokenVerifier.verify(credential);

        // 개인 Gmail 계정은 hostedDomain이 null이라 여기서 막힙니다.
        if (account.hostedDomain() == null || !allowedDomains.contains(account.hostedDomain())) {
            throw new ForbiddenException("학교 계정(" + String.join(", ", allowedDomains) + ")으로만 로그인할 수 있습니다");
        }

        Member member = memberRepository.findByEmail(account.email())
                .orElseGet(() -> memberRepository.save(createGoogleMember(account)));
        promoteIfListed(member);
        // token은 올린 뒤에 만듭니다. 그래서 올린 로그인의 token부터 교수가 담깁니다.
        return createLoginTokens(member);
    }

    // cookie의 refresh token으로 새 access token과 새 refresh token을 줍니다(교체). 쓴 refresh token은 지워져 다시 쓸 수 없습니다.
    // 검사 순서: Origin → cookie → 데이터베이스의 행 → 만료 시각. Origin이 틀리면 403, 나머지는 401입니다.
    // 교수로 올리기(X-1)는 하지 않습니다. 구글 로그인 때만 합니다.
    @Transactional
    public LoginTokens refresh(String origin, String refreshToken) {
        checkOrigin(origin);
        RefreshToken used = refreshTokenService.use(refreshToken);
        // 만료 시각은 쓴 token의 것을 그대로 물려줍니다. 재발급해도 로그인한 시각부터 24시간이 지나면 끝납니다(절대 만료).
        // 회원은 데이터베이스에서 읽은 것이라, access token의 memberType도 지금의 회원 유형입니다.
        return createTokens(used.getMember(), used.getExpiresAt());
    }

    // 로그아웃: cookie의 refresh token을 지웁니다. cookie가 없거나 이미 지운 값이어도 성공입니다.
    // 이미 준 access token은 서버가 저장하지 않아서 지울 수 없고, 만료(최대 10분)까지 유효합니다.
    @Transactional
    public void logout(String origin, String refreshToken) {
        checkOrigin(origin);
        refreshTokenService.delete(refreshToken);
    }

    // refresh token(cookie)을 쓰는 요청이 허용한 화면 주소에서 왔는지 봅니다. 아니면 403입니다.
    // cookie는 브라우저가 알아서 붙이므로, 다른 사이트가 우리 주소로 요청을 보내게 하는 공격(CSRF)을 막는 둘째 장치입니다(첫째는 SameSite=Strict).
    // Origin이 없는 요청(Postman, 테스트)은 통과합니다. 브라우저는 POST에 Origin을 항상 붙입니다.
    private void checkOrigin(String origin) {
        if (origin != null && !allowedOrigins.contains(origin)) {
            throw new ForbiddenException("허용하지 않은 주소에서 온 요청입니다");
        }
    }

    // 로그인에 성공한 회원에게 token 두 개를 줍니다. 그 회원의 만료된 refresh token을 먼저 지워 행이 쌓이지 않게 합니다.
    private LoginTokens createLoginTokens(Member member) {
        refreshTokenService.deleteExpired(member);
        return createTokens(member, refreshTokenService.expiresAtFromNow());
    }

    // access token과, 정한 시각에 만료되는 refresh token을 만듭니다.
    private LoginTokens createTokens(Member member, LocalDateTime refreshTokenExpiresAt) {
        String refreshToken = refreshTokenService.issue(member, refreshTokenExpiresAt);
        return new LoginTokens(jwtProvider.createToken(member), refreshToken, refreshTokenExpiresAt);
    }

    // 구글 계정으로 새 회원을 만듭니다. 교수 이메일 목록에 있으면 교수, 아니면 학생입니다. 비밀번호는 받지 않아서 null입니다.
    private Member createGoogleMember(GoogleAccount account) {
        MemberType memberType = isProfessorEmail(account.email()) ? MemberType.PROFESSOR : MemberType.STUDENT;
        return new Member(account.email(), null, account.name(), memberType);
    }

    // 교수 목록에 있는 학생을 교수로 올립니다(X-1). 교수를 학생으로 내리지는 않습니다.
    private void promoteIfListed(Member member) {
        if (member.getMemberType() != MemberType.STUDENT || !isProfessorEmail(member.getEmail())) {
            return;
        }
        // 조원·조장 기록이 있는 학생은 올리지 않습니다. 올리면 교수가 조원으로 남아 "조원은 모두 학생"이 깨집니다.
        if (teamMemberRepository.existsByMemberId(member.getId())) {
            return;
        }
        // save를 다시 부르지 않습니다. @Transactional 안에서 읽은 Entity는 값이 바뀌면 끝날 때 JPA가 UPDATE 합니다(변경 감지).
        member.promoteToProfessor();
    }

    // 이메일이 교수 목록에 있는지 봅니다. 목록은 생성자에서 소문자로 바꿔 두어서 대소문자를 가리지 않습니다.
    private boolean isProfessorEmail(String email) {
        return professorEmails.contains(email.toLowerCase());
    }
}
