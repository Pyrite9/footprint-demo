package com.footprint.backend.dto;

import com.footprint.backend.entity.Member;
import com.footprint.backend.entity.MemberType;

// 회원 정보를 클라이언트에 돌려줄 때 쓰는 응답 body의 모양입니다. 비밀번호는 넣지 않습니다.
public record MemberResponse(Long id, String email, String name, MemberType memberType) {

    // 회원 Entity를 응답 DTO로 바꿉니다.
    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getName(), member.getMemberType());
    }
}
