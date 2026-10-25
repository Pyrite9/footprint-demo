package com.footprint.backend.controller;

import com.footprint.backend.dto.MemberResponse;
import com.footprint.backend.service.MemberService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 회원 정보 요청을 받아 Service를 호출하고, 결과를 응답 DTO로 바꿔 돌려줍니다.
@RestController
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    // 로그인한 회원 자신의 정보를 돌려줍니다.
    // @AuthenticationPrincipal은 JwtAuthenticationFilter가 token에서 꺼내 적어 둔 회원 번호를 넣어 줍니다.
    @GetMapping("/me")
    public MemberResponse me(@AuthenticationPrincipal Long memberId) {
        return MemberResponse.from(memberService.findById(memberId));
    }
}
