package com.footprint.backend.service;

import com.footprint.backend.entity.Member;
import com.footprint.backend.exception.UnauthorizedException;
import com.footprint.backend.repository.MemberRepository;
import com.footprint.backend.security.JwtProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

// 이메일과 비밀번호를 확인하고 token을 발급합니다.
@Service
public class AuthService {

    // 이메일이 없을 때와 비밀번호가 틀렸을 때 같은 메시지를 줍니다. 어느 이메일이 가입돼 있는지 알려 주지 않기 위해서입니다.
    private static final String LOGIN_FAILED_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    // 로그인에 성공하면 token 문자열을 돌려줍니다. 실패하면 UnauthorizedException을 던집니다.
    public String login(String email, String password) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException(LOGIN_FAILED_MESSAGE));

        // matches(입력한 비밀번호, 저장된 BCrypt 값) 순서입니다. BCrypt는 되돌릴 수 없어서 입력값을 같은 방식으로 바꿔 비교합니다.
        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new UnauthorizedException(LOGIN_FAILED_MESSAGE);
        }
        return jwtProvider.createToken(member);
    }
}
