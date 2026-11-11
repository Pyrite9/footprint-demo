package com.footprint.backend.service;

import com.footprint.backend.entity.Member;
import com.footprint.backend.exception.NotFoundException;
import com.footprint.backend.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 회원을 조회하고, 개인정보 고지 동의를 기록합니다. 대상이 없으면 NotFoundException을 던집니다.
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

    // 로그인한 회원 본인이 개인정보 고지에 동의합니다(X-2). 이미 동의했으면 처음 동의한 시각을 그대로 둡니다.
    // 주소에 회원 번호가 없고 token의 회원만 다루므로, 다른 회원의 동의를 기록할 길이 없습니다.
    // @Transactional: 트랜잭션이 끝날 때 바뀐 동의 시각이 데이터베이스에 저장됩니다(save를 따로 부르지 않습니다).
    @Transactional
    public Member consentToPrivacy(Long memberId) {
        Member member = findById(memberId);
        if (!member.hasConsentedToPrivacy()) {
            member.consentToPrivacy();
        }
        return member;
    }
}
