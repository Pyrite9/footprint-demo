package com.footprint.backend.dto;

// 로그인에 성공했을 때 돌려주는 응답 body의 모양입니다. 화면은 이 token을 이후 요청의 Authorization header에 넣습니다.
public record LoginResponse(String accessToken) {
}
