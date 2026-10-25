package com.footprint.backend.exception;

import com.footprint.backend.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 던져진 예외를 받아 status와 ApiErrorResponse로 바꿔 응답합니다.
// token 없이 막힌 주소에 온 요청의 401은 Controller 앞에서 막히므로 여기가 아니라 SecurityConfig에서 처리합니다.
// 여기의 401은 로그인 API가 이메일·비밀번호를 거절한 경우입니다.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 대상이 없을 때 404 NOT_FOUND로 응답합니다.
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleNotFound(NotFoundException e) {
        return new ApiErrorResponse("NOT_FOUND", e.getMessage());
    }

    // 로그인에 실패했을 때 401 UNAUTHORIZED로 응답합니다.
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiErrorResponse handleUnauthorized(UnauthorizedException e) {
        return new ApiErrorResponse("UNAUTHORIZED", e.getMessage());
    }

    // 접근 권한이 없을 때 403 FORBIDDEN으로 응답합니다.
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiErrorResponse handleForbidden(ForbiddenException e) {
        return new ApiErrorResponse("FORBIDDEN", e.getMessage());
    }

    // 이미 있는 것을 다시 만들려 할 때 409 DUPLICATE로 응답합니다.
    @ExceptionHandler(DuplicateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleDuplicate(DuplicateException e) {
        return new ApiErrorResponse("DUPLICATE", e.getMessage());
    }

    // Service가 업무 규칙에 맞지 않는 입력값을 찾았을 때 400 INVALID_INPUT으로 응답합니다.
    @ExceptionHandler(InvalidInputException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidInput(InvalidInputException e) {
        return new ApiErrorResponse("INVALID_INPUT", e.getMessage());
    }

    // @Valid 검사에 걸린 입력값을 400 INVALID_INPUT으로 응답합니다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleNotValid(MethodArgumentNotValidException e) {
        return new ApiErrorResponse("INVALID_INPUT", "입력값이 올바르지 않습니다");
    }

    // JSON으로 읽을 수 없는 요청 body를 400 INVALID_INPUT으로 응답합니다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleNotReadable(HttpMessageNotReadableException e) {
        return new ApiErrorResponse("INVALID_INPUT", "요청 body의 형식이 올바르지 않습니다");
    }

}
