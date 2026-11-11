package com.footprint.backend.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.footprint.backend.dto.ApiErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
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

import java.lang.annotation.Annotation;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

// 던져진 예외를 받아 status와 ApiErrorResponse로 바꿔 응답합니다.
// token 없이 막힌 주소에 온 요청의 401은 Controller 앞에서 막히므로 여기가 아니라 SecurityConfig에서 처리합니다.
// 여기의 401은 로그인 API가 이메일·비밀번호를 거절한 경우입니다.
// 위에서부터 더 구체적인 예외의 handler가 먼저 고릅니다. 어디에도 맞지 않는 예외는 맨 아래 handleException이 받습니다.
// 형식 오류의 400은 message 끝에 틀린 칸을 "칸 이름: 이유"로 붙입니다(X-7). 칸 이름은 요청 JSON의 필드 이름이고, 응답 모양은 그대로 { code, message }입니다.
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

    // @Valid 검사에 걸린 입력값을 400 INVALID_INPUT으로 응답합니다. 틀린 칸을 모두 적습니다.
    // 예: "입력값이 올바르지 않습니다. startDate: 값이 없습니다, title: 비어 있습니다"
    // 검사기가 칸을 보는 순서는 정해져 있지 않아서, "칸 이름: 이유" 글자를 문자 코드 순으로 정렬해 순서를 고정합니다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        List<String> fields = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + constraintReasonOf(error))
                .sorted()
                .toList();
        return new ApiErrorResponse("INVALID_INPUT", withFields("입력값이 올바르지 않습니다", fields));
    }

    // 주소의 값이 받을 타입과 맞지 않을 때(예: /assignments/abc) 400 INVALID_INPUT으로 응답합니다.
    // 예: "주소의 값이 올바르지 않습니다. assignmentId: 정수여야 합니다". 칸 이름은 주소의 {assignmentId} 자리 이름입니다.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        String field = e.getName() + ": " + typeReasonOf(e.getRequiredType());
        return new ApiErrorResponse("INVALID_INPUT", withFields("주소의 값이 올바르지 않습니다", List.of(field)));
    }

    // 업로드 요청에 file 칸이 빠졌을 때 400 INVALID_INPUT으로 응답합니다. @NotNull에 걸린 칸과 같은 문장입니다.
    // 예: "입력값이 올바르지 않습니다. file: 값이 없습니다"
    @ExceptionHandler(MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMissingServletRequestPart(MissingServletRequestPartException e) {
        String field = e.getRequestPartName() + ": 값이 없습니다";
        return new ApiErrorResponse("INVALID_INPUT", withFields("입력값이 올바르지 않습니다", List.of(field)));
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
    // 칸의 타입이 틀렸으면(예: 점수에 4.5나 "5") 그 칸을 적습니다. 예: "요청 body의 형식이 올바르지 않습니다. evaluations[0].goalSharing: 정수여야 합니다"
    // Jackson은 JSON을 앞에서부터 읽다가 처음 틀린 칸에서 멈추므로 한 칸만 적힙니다.
    // JSON 문법이 깨졌거나 body가 없으면 칸을 알 수 없어서 앞 문장만 보냅니다. Jackson의 원문 메시지(클래스 이름이 들어 있음)는 보내지 않습니다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        List<String> fields = List.of();
        // 타입이 틀린 경우 원인(getCause)이 MismatchedInputException이고, 그 안에 틀린 칸의 경로와 받으려던 타입이 있습니다.
        if (e.getCause() instanceof MismatchedInputException cause && !cause.getPath().isEmpty()) {
            fields = List.of(jsonPathOf(cause) + ": " + typeReasonOf(cause.getTargetType()));
        }
        return new ApiErrorResponse("INVALID_INPUT", withFields("요청 body의 형식이 올바르지 않습니다", fields));
    }

    // 없는 주소(예: /nope)에 온 요청을 404 NOT_FOUND로 응답합니다. 없으면 Spring 기본 모양({ timestamp, status, ... })이 나갑니다.
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleNoResourceFound(NoResourceFoundException e) {
        return new ApiErrorResponse("NOT_FOUND", "없는 주소입니다");
    }

    // 주소는 있지만 그 method가 없는 요청(예: DELETE /assignments/1)도 "없는 주소"로 보고 404 NOT_FOUND로 응답합니다.
    // 405용 code를 따로 두지 않고 정해 둔 code 목록 안에서 처리하기 위해서입니다. 가짜 API도 같은 응답입니다.
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

    // 앞 문장 뒤에 틀린 칸들을 ". "과 ", "로 이어 붙입니다. 칸이 없으면 앞 문장만 돌려줍니다.
    private static String withFields(String sentence, List<String> fields) {
        if (fields.isEmpty()) {
            return sentence;
        }
        return sentence + ". " + String.join(", ", fields);
    }

    // @Valid 검사에 걸린 칸의 이유를 한국어로 만듭니다. 서버 언어 설정에 따라 바뀌는 검사기 기본 문구는 쓰지 않습니다.
    private static String constraintReasonOf(FieldError error) {
        // FieldError 안에는 검사기가 만든 ConstraintViolation이 들어 있습니다. 거기서 걸린 어노테이션(@Size 등)과 그 값(max 등)을 꺼냅니다.
        if (!error.contains(ConstraintViolation.class)) {
            return "올바르지 않습니다";
        }
        Annotation constraint = error.unwrap(ConstraintViolation.class).getConstraintDescriptor().getAnnotation();
        if (constraint instanceof NotBlank || constraint instanceof NotEmpty) {
            return "비어 있습니다";
        }
        if (constraint instanceof NotNull) {
            return "값이 없습니다";
        }
        // 이 프로젝트의 @Size는 모두 글자 칸에 붙어 있어서 "자"로 적습니다.
        if (constraint instanceof Size size) {
            return size.max() + "자 이하여야 합니다";
        }
        if (constraint instanceof Min min) {
            return min.value() + " 이상이어야 합니다";
        }
        if (constraint instanceof Max max) {
            return max.value() + " 이하여야 합니다";
        }
        return "올바르지 않습니다";
    }

    // 받으려던 Java 타입으로 그 칸이 "무엇이어야 하는지"를 만듭니다. 클래스 이름은 응답에 넣지 않습니다.
    private static String typeReasonOf(Class<?> type) {
        if (type == Long.class || type == Integer.class || type == long.class || type == int.class) {
            return "정수여야 합니다";
        }
        if (type == String.class) {
            return "문자열이어야 합니다";
        }
        if (type == LocalDate.class) {
            return "yyyy-MM-dd 형식의 날짜여야 합니다";
        }
        if (type != null && Collection.class.isAssignableFrom(type)) {
            return "배열이어야 합니다";
        }
        return "형식이 올바르지 않습니다";
    }

    // Jackson이 멈춘 자리를 요청 JSON의 경로로 바꿉니다. 예: [evaluations, 0, goalSharing] → "evaluations[0].goalSharing"
    // 경로에는 DTO에 있는 필드 이름과 배열 번호만 들어갑니다. DTO에 없는 칸은 Jackson이 읽지 않고 넘어갑니다.
    private static String jsonPathOf(JsonMappingException e) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : e.getPath()) {
            if (reference.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getFieldName());
            } else {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
