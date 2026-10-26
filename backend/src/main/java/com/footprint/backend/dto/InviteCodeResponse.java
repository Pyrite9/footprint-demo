package com.footprint.backend.dto;

import com.footprint.backend.entity.Team;

// 초대 코드 응답의 모양입니다. 발급한 적이 없으면 inviteCode가 null입니다.
public record InviteCodeResponse(String inviteCode) {

    // 조 Entity에서 초대 코드만 꺼내 응답 DTO로 바꿉니다.
    public static InviteCodeResponse from(Team team) {
        return new InviteCodeResponse(team.getInviteCode());
    }
}
