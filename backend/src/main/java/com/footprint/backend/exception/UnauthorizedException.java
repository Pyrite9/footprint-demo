package com.footprint.backend.exception;

// 로그인에 실패했을 때 Service가 던지는 예외입니다. GlobalExceptionHandler가 401 응답으로 바꿉니다.
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
