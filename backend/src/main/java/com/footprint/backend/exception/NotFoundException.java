package com.footprint.backend.exception;

// 요청한 대상이 없을 때 Service가 던지는 예외입니다. GlobalExceptionHandler가 404 응답으로 바꿉니다.
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
