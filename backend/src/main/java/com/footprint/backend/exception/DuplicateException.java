package com.footprint.backend.exception;

// 이미 있는 것을 다시 만들려고 할 때 Service가 던지는 예외입니다. GlobalExceptionHandler가 409 응답으로 바꿉니다.
public class DuplicateException extends RuntimeException {
    public DuplicateException(String message) {
        super(message);
    }
}
