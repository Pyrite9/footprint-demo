package com.footprint.backend.dto;

// 초대 코드 응답의 모양입니다. 발급한 적이 없으면 inviteCode가 null입니다.
public record InviteCodeResponse(String inviteCode) { }
