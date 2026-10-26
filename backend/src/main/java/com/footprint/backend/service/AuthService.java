package com.footprint.backend.service;

import com.footprint.backend.entity.Member;
import com.footprint.backend.entity.MemberType;
import com.footprint.backend.exception.ForbiddenException;
import com.footprint.backend.exception.UnauthorizedException;
import com.footprint.backend.repository.MemberRepository;
import com.footprint.backend.security.GoogleTokenVerifier;
import com.footprint.backend.security.GoogleTokenVerifier.GoogleAccount;
import com.footprint.backend.security.JwtProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 로그인을 확인하고 token을 발급합니다. 12.11 시연은 구글 로그인이 기본이고, 이메일·비밀번호 로그인은 시작 계정용(개발·백업)입니다.
@Service
public class AuthService {

    // 이메일이 없을 때와 비밀번호가 틀렸을 때 같은 메시지를 줍니다. 어느 이메일이 가입돼 있는지 알려 주지 않기 위해서입니다.
    private static final String LOGIN_FAILED_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final List<String> allowedDomains;
    private final List<String> professorEmails;

    // allowedDomains와 professorEmails는 application.yml의 쉼표로 구분한 값을 Spring이 목록으로 바꿔 넣어 줍니다.
    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider,
                       GoogleTokenVerifier googleTokenVerifier,
                       @Value("${google.allowed-domains}") List<String> allowedDomains,
                       @Value("${google.professor-emails}") List<String> professorEmails) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.googleTokenVerifier = googleTokenVerifier;
        this.allowedDomains = allowedDomains;
        // 대소문자를 가리지 않고 비교하려고 소문자로 바꿔 둡니다.
        this.professorEmails = professorEmails.stream().map(String::toLowerCase).toList();
    }

    // 이메일과 비밀번호로 로그인합니다. 성공하면 token 문자열을 돌려줍니다. 실패하면 UnauthorizedException을 던집니다.
    public String login(String email, String password) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException(LOGIN_FAILED_MESSAGE));

        // 구글로 만든 회원은 비밀번호가 없어서 이 방법으로 로그인할 수 없습니다.
        // matches(입력한 비밀번호, 저장된 BCrypt 값) 순서입니다. BCrypt는 되돌릴 수 없어서 입력값을 같은 방식으로 바꿔 비교합니다.
        if (member.getPassword() == null || !passwordEncoder.matches(password, member.getPassword())) {
            throw new UnauthorizedException(LOGIN_FAILED_MESSAGE);
        }
        return jwtProvider.createToken(member);
    }

    // 구글 계정으로 로그인합니다. 처음 온 계정이면 회원으로 저장합니다(M-1-1 회원 등록). token 문자열을 돌려줍니다.
    @Transactional
    public String loginWithGoogle(String credential) {
        GoogleAccount account = googleTokenVerifier.verify(credential);

        // 개인 Gmail 계정은 hostedDomain이 null이라 여기서 막힙니다.
        if (account.hostedDomain() == null || !allowedDomains.contains(account.hostedDomain())) {
            throw new ForbiddenException("학교 계정(" + String.join(", ", allowedDomains) + ")으로만 로그인할 수 있습니다");
        }

        Member member = memberRepository.findByEmail(account.email())
                .orElseGet(() -> memberRepository.save(createGoogleMember(account)));
        return jwtProvider.createToken(member);
    }

    // 구글 계정으로 새 회원을 만듭니다. 교수 이메일 목록에 있으면 교수, 아니면 학생입니다. 비밀번호는 받지 않아서 null입니다.
    private Member createGoogleMember(GoogleAccount account) {
        MemberType memberType = professorEmails.contains(account.email().toLowerCase())
                ? MemberType.PROFESSOR : MemberType.STUDENT;
        return new Member(account.email(), null, account.name(), memberType);
    }
}
