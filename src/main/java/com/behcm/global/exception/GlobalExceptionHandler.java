package com.behcm.global.exception;

import com.behcm.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.event.Level;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 예외 → 응답 + 로그.
 *
 * <p>로그 레벨은 "누가 고쳐야 하는가"로 정한다({@link ErrorLogLevel}). 예외가 났다고 전부 ERROR 로 찍으면
 * "오늘 이미 인증했습니다"가 스택트레이스와 함께 쌓여 진짜 ERROR 가 묻힌다. 스택트레이스는 우리가
 * 고쳐야 하는 경우(500, 무결성 위반)에만 남긴다 — 나머지는 한 줄이면 원인을 안다.
 *
 * <p>요청 경로·행위자는 {@code RequestLoggingFilter} 가 MDC(requestId, memberId)로 넣으므로 여기서는
 * 예외 종류와 메서드/경로만 찍는다.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Object>> handleCustomException(CustomException e, HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        Level level = ErrorLogLevel.of(errorCode);
        var event = log.atLevel(level);
        if (level == Level.ERROR) {
            event = event.setCause(e);
        }
        event.log("[{}] {} - {}", errorCode, describe(request), e.getMessage());

        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.error(e.getMessage()));
    }

    // 로그인 실패는 사용자 사정이지만, 급증하면 무차별 대입이므로 추세를 볼 수 있게 WARN 으로 남긴다.
    // 이메일은 컨트롤러 요청 본문에만 있어 여기서는 알 수 없다 — 필요하면 AuthService 에서 마스킹해 남긴다.
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentialsException(BadCredentialsException e, HttpServletRequest request) {
        log.warn("[INVALID_CREDENTIALS] {} - {}", describe(request), e.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_CREDENTIALS.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_CREDENTIALS.getMessage()));
    }

    // 검증 실패는 FE 가 잘못된 값을 보낸 것이다. 어떤 필드가 왜 거절됐는지 없으면 FE 버그를 못 찾으므로
    // 필드 오류 전부를 한 줄에 남긴다. 스택트레이스는 프레임워크 내부라 도움이 안 된다.
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Object>> handleValidationException(Exception e, HttpServletRequest request) {
        BindingResult bindingResult = e instanceof MethodArgumentNotValidException valid
                ? valid.getBindingResult()
                : ((BindException) e).getBindingResult();

        String fieldErrors = bindingResult.getFieldErrors().stream()
                .map(error -> error.getField() + "=" + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("[VALIDATION_FAILED] {} - {}", describe(request), fieldErrors);

        FieldError firstError = bindingResult.getFieldError();
        String message = firstError != null && firstError.getDefaultMessage() != null
                ? firstError.getDefaultMessage()
                : "입력값이 올바르지 않습니다.";
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message));
    }

    /**
     * 요청 자체가 규격에 맞지 않는 경우(경로변수 타입 불일치, 깨진 JSON, 필수 파라미터/파트 누락).
     * 이 핸들러가 없으면 catch-all 로 떨어져 500 + ERROR 가 된다 — 클라이언트 잘못을 서버 장애처럼 보이게 한다.
     */
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiResponse<Object>> handleClientRequestException(Exception e, HttpServletRequest request) {
        log.warn("[{}] {} - {}", e.getClass().getSimpleName(), describe(request), summarize(e));
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("[UPLOAD_SIZE_EXCEEDED] {} - maxUploadSize={}", describe(request), e.getMaxUploadSize());
        return ResponseEntity.status(ErrorCode.UPLOAD_SIZE_EXCEEDED.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.UPLOAD_SIZE_EXCEEDED.getMessage()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.info("[METHOD_NOT_ALLOWED] {} - supported={}", describe(request), e.getSupportedHttpMethods());
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResourceFoundException(NoResourceFoundException e, HttpServletRequest request) {
        log.info("[NOT_FOUND] {}", describe(request));
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.NOT_FOUND.getMessage()));
    }

    // @PreAuthorize 거부는 AuthorizationDeniedException(= AccessDeniedException)으로 컨트롤러 진입 전에
    // 던져진다. 이 핸들러가 없으면 아래 catch-all 에 걸려 403 대신 500 이 나간다.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request) {
        log.warn("[ACCESS_DENIED] {} - {}", describe(request), e.getMessage());
        return ResponseEntity.status(ErrorCode.ACCESS_DENIED.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.ACCESS_DENIED.getMessage()));
    }

    /**
     * UK/FK 위반이 DB 까지 내려갔다는 것은 서비스 로직이 사전 검증을 놓쳤다는 뜻이다(동시 요청 경합 포함).
     * 사용자에게는 409 로 재시도를 안내하되, 로그는 ERROR — 어느 제약이 어디서 깨졌는지 개발자가 봐야 한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolationException(DataIntegrityViolationException e, HttpServletRequest request) {
        log.error("[DATA_CONFLICT] {} - {}", describe(request), e.getMostSpecificCause().getMessage(), e);
        return ResponseEntity.status(ErrorCode.DATA_CONFLICT.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.DATA_CONFLICT.getMessage()));
    }

    /**
     * 클라이언트가 응답을 받기 전에 연결을 끊었다(화면 이동, 새로고침). 응답을 쓸 곳이 없으므로
     * 아무것도 돌려주지 않고, 서버 잘못이 아니므로 DEBUG 로만 남긴다.
     */
    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbortException(ClientAbortException e, HttpServletRequest request) {
        log.debug("[CLIENT_ABORT] {}", describe(request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e, HttpServletRequest request) {
        log.error("[UNHANDLED] {} - {}: {}", describe(request), e.getClass().getName(), e.getMessage(), e);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }

    private static String describe(HttpServletRequest request) {
        return request.getMethod() + " " + request.getRequestURI();
    }

    /**
     * 프레임워크 예외 메시지는 스택 일부를 통째로 담고 있어 길다. 원인 파악에 필요한 만큼만 남긴다.
     */
    private static String summarize(Exception e) {
        if (e instanceof MethodArgumentTypeMismatchException mismatch) {
            return "parameter '" + mismatch.getName() + "' value '" + mismatch.getValue()
                    + "' is not " + (mismatch.getRequiredType() != null ? mismatch.getRequiredType().getSimpleName() : "?");
        }
        if (e instanceof MissingServletRequestParameterException missing) {
            return "missing parameter '" + missing.getParameterName() + "'";
        }
        if (e instanceof MissingServletRequestPartException missing) {
            return "missing part '" + missing.getRequestPartName() + "'";
        }
        if (e instanceof HttpMessageNotReadableException unreadable) {
            Throwable cause = unreadable.getMostSpecificCause();
            String message = cause.getMessage();
            // Jackson 메시지는 첫 줄에 원인이 있고 뒤에는 위치 정보가 붙는다.
            return message == null ? cause.getClass().getSimpleName() : message.lines().findFirst().orElse(message);
        }
        return e.getMessage();
    }
}
