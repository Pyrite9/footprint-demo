package com.footprint.backend.exception;

// 로그인했지만 그 기록에 접근할 권한이 없을 때 Service가 던지는 예외입니다. GlobalExceptionHandler가 403 응답으로 바꿉니다.
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
