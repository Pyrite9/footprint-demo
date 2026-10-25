package com.footprint.backend.service;

import com.footprint.backend.entity.Member;
import com.footprint.backend.exception.NotFoundException;
import com.footprint.backend.repository.MemberRepository;
import org.springframework.stereotype.Service;

// 회원을 조회합니다. 대상이 없으면 NotFoundException을 던집니다.
@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    // 회원 번호로 회원을 찾습니다.
    public Member findById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("회원이 없습니다. id=" + id));
    }
}
