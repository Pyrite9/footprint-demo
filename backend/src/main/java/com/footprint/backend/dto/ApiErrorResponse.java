package com.footprint.backend.dto;

// 오류가 났을 때 돌려주는 응답 body의 모양입니다.
public record ApiErrorResponse (String code, String message) {

}
