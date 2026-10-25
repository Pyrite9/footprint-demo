package com.footprint.backend.exception;

import com.footprint.backend.dto.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// 던져진 예외를 받아 status와 ApiErrorResponse로 바꿔 응답합니다.
// token 없이 막힌 주소에 온 요청의 401은 Controller 앞에서 막히므로 여기가 아니라 SecurityConfig에서 처리합니다.
// 여기의 401은 로그인 API가 이메일·비밀번호를 거절한 경우입니다.
// 위에서부터 더 구체적인 예외의 handler가 먼저 고릅니다. 어디에도 맞지 않는 예외는 맨 아래 handleException이 받습니다.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 예상하지 못한 예외의 원인을 서버 로그에 남길 때 씁니다.
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
    public ApiErrorResponse handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        return new ApiErrorResponse("INVALID_INPUT", "입력값이 올바르지 않습니다");
    }

    // 주소의 값이 받을 타입과 맞지 않을 때(예: /assignments/abc) 400 INVALID_INPUT으로 응답합니다.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        return new ApiErrorResponse("INVALID_INPUT", "주소의 값이 올바르지 않습니다");
    }

    // 업로드 요청에 file 칸이 빠졌을 때 400 INVALID_INPUT으로 응답합니다.
    @ExceptionHandler(MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMissingServletRequestPart(MissingServletRequestPartException e) {
        return new ApiErrorResponse("INVALID_INPUT", "파일이 없습니다");
    }

    // multipart 요청을 읽지 못했을 때 400 INVALID_INPUT으로 응답합니다.
    // 20MB를 넘는 파일(MaxUploadSizeExceededException)도 MultipartException의 한 종류라 여기로 옵니다.
    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMultipart(MultipartException e) {
        if (e instanceof MaxUploadSizeExceededException) {
            return new ApiErrorResponse("INVALID_INPUT", "파일은 20MB까지 올릴 수 있습니다");
        }
        return new ApiErrorResponse("INVALID_INPUT", "파일 업로드 요청의 형식이 올바르지 않습니다");
    }

    // JSON으로 읽을 수 없는 요청 body를 400 INVALID_INPUT으로 응답합니다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return new ApiErrorResponse("INVALID_INPUT", "요청 body의 형식이 올바르지 않습니다");
    }

    // 없는 주소(예: /nope)에 온 요청을 404 NOT_FOUND로 응답합니다. 없으면 Spring 기본 모양({ timestamp, status, ... })이 나갑니다.
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleNoResourceFound(NoResourceFoundException e) {
        return new ApiErrorResponse("NOT_FOUND", "없는 주소입니다");
    }

    // 주소는 있지만 그 method가 없는 요청(예: DELETE /assignments/1)도 "없는 주소"로 보고 404 NOT_FOUND로 응답합니다.
    // 405용 code를 따로 두지 않고 다섯 가지 code 안에서 처리하기 위해서입니다. 가짜 API도 같은 응답입니다.
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return new ApiErrorResponse("NOT_FOUND", "없는 주소입니다");
    }

    // 위 handler들이 받지 않은 나머지 예외를 받습니다. 이름은 규칙대로면 handle만 남아서 handleException으로 둡니다.
    // 상태 코드가 요청마다 달라서 @ResponseStatus 대신 ResponseEntity로 상태와 body를 함께 돌려줍니다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception e) {
        // Spring이 "요청이 틀렸다"고 판단한 예외(ErrorResponse 중 4xx, 예: 지원하지 않는 Content-Type)는 서버 오류가 아닙니다.
        // code 목록에 맞춰 400 INVALID_INPUT으로 돌려줍니다.
        if (e instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiErrorResponse("INVALID_INPUT", "요청의 형식이 올바르지 않습니다"));
        }
        // 그 밖은 서버의 잘못(500)입니다. 원인은 로그에만 남기고, 응답에는 내부 내용(예외 메시지)을 담지 않습니다.
        log.error("처리하지 못한 예외입니다", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("SERVER_ERROR", "서버에서 오류가 났습니다"));
    }
}
