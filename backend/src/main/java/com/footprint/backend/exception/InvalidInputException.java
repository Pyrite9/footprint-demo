package com.footprint.backend.exception;

// 요청 body의 모양은 맞지만 업무 규칙에 맞지 않을 때 Service가 던지는 예외입니다. GlobalExceptionHandler가 400 응답으로 바꿉니다.
// 예: 종료일이 시작일보다 앞이거나, 가입하지 않은 이메일을 조장으로 지정했을 때입니다.
public class InvalidInputException extends RuntimeException {
    public InvalidInputException(String message) {
        super(message);
    }
}
