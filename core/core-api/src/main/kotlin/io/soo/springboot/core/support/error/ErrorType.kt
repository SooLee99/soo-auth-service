package io.soo.springboot.core.support.error

import org.springframework.boot.logging.LogLevel
import org.springframework.http.HttpStatus

enum class ErrorType(
    val status: HttpStatus,
    val code: ErrorCode,
    val message: String,
    val logLevel: LogLevel,
) {
    // 400
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, ErrorCode.E400, "요청이 올바르지 않습니다.", LogLevel.WARN),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, ErrorCode.E400, "요청 본문이 올바른 형식이 아닙니다.", LogLevel.WARN),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, ErrorCode.E400, "입력값이 올바르지 않습니다.", LogLevel.WARN),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, ErrorCode.E400, "요청 파라미터가 올바르지 않습니다.", LogLevel.WARN),

    // 토큰 요청값 누락/형식 문제 (400)
    REFRESH_TOKEN_REQUIRED(HttpStatus.BAD_REQUEST, ErrorCode.E400, "리프레시 토큰이 필요합니다.", LogLevel.WARN),

    // 401
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, ErrorCode.E401, "인증이 필요합니다.", LogLevel.WARN),

    // 일반 인증 실패
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, ErrorCode.E401, "이메일 또는 비밀번호가 올바르지 않습니다.", LogLevel.WARN),

    // 로컬 로그인 정책 전용 인증 실패 (외부 메시지는 동일하게 유지)
    LOGIN_BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, ErrorCode.E401, "이메일 또는 비밀번호가 올바르지 않습니다.", LogLevel.WARN),
    LOGIN_ACCOUNT_NOT_FOUND(HttpStatus.UNAUTHORIZED, ErrorCode.E401, "이메일 또는 비밀번호가 올바르지 않습니다.", LogLevel.WARN),
    LOGIN_DENIED(HttpStatus.UNAUTHORIZED, ErrorCode.E401, "이메일 또는 비밀번호가 올바르지 않습니다.", LogLevel.WARN),

    // Access Token
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, ErrorCode.E401, "유효하지 않은 액세스 토큰입니다.", LogLevel.WARN),
    EXPIRED_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, ErrorCode.E401, "만료된 액세스 토큰입니다.", LogLevel.WARN),

    // Refresh Token
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, ErrorCode.E401, "유효하지 않은 리프레시 토큰입니다.", LogLevel.WARN),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, ErrorCode.E401, "만료된 리프레시 토큰입니다.", LogLevel.WARN),
    REVOKED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, ErrorCode.E401, "폐기된 리프레시 토큰입니다.", LogLevel.WARN),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, ErrorCode.E401, "이미 사용된 리프레시 토큰입니다.", LogLevel.WARN),
    REFRESH_TOKEN_DEVICE_MISMATCH(HttpStatus.UNAUTHORIZED, ErrorCode.E401, "리프레시 토큰의 디바이스 정보가 일치하지 않습니다.", LogLevel.WARN),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, ErrorCode.E403, "접근 권한이 없습니다.", LogLevel.WARN),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, ErrorCode.E403, "비활성화된 계정입니다.", LogLevel.WARN),
    ACCOUNT_BLOCKED(HttpStatus.FORBIDDEN, ErrorCode.E403, "차단된 계정입니다.", LogLevel.WARN),
    ACCOUNT_SOFT_DELETED(HttpStatus.FORBIDDEN, ErrorCode.E403, "탈퇴 처리된 계정입니다.", LogLevel.WARN),

    // 404
    NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E404, "요청한 리소스를 찾을 수 없습니다.", LogLevel.WARN),

    // 405
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.E405, "지원하지 않는 HTTP 메서드입니다.", LogLevel.WARN),

    // 406
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, ErrorCode.E406, "요청한 응답 형식을 제공할 수 없습니다.", LogLevel.WARN),

    // 409
    CONFLICT(HttpStatus.CONFLICT, ErrorCode.E409, "요청이 현재 상태와 충돌합니다.", LogLevel.WARN),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, ErrorCode.E409, "이미 존재하는 이메일입니다.", LogLevel.WARN),
    DUPLICATE_PHONE_NUMBER(HttpStatus.CONFLICT, ErrorCode.E409, "이미 존재하는 전화번호입니다.", LogLevel.WARN),
    DUPLICATE_SERVICE_CODE(HttpStatus.CONFLICT, ErrorCode.E409, "이미 존재하는 서비스 코드입니다.", LogLevel.WARN),
    SERVICE_INACTIVE(HttpStatus.CONFLICT, ErrorCode.E409, "비활성화된 서비스입니다.", LogLevel.WARN),
    SERVICE_HAS_ACTIVE_MEMBERSHIPS(HttpStatus.CONFLICT, ErrorCode.E409, "활성 멤버십이 존재하여 서비스를 비활성화할 수 없습니다.", LogLevel.WARN),

    // 413
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.E413, "업로드 용량이 너무 큽니다.", LogLevel.WARN),

    // 415
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.E415, "지원하지 않는 Content-Type 입니다.", LogLevel.WARN),

    // 429
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.E429, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", LogLevel.WARN),

    // 로컬 로그인 정책 전용 제한
    LOGIN_ATTEMPTS_EXCEEDED(
        HttpStatus.TOO_MANY_REQUESTS,
        ErrorCode.E429,
        "로그인 시도 횟수가 초과되었습니다. 잠시 후 다시 시도해 주세요.",
        LogLevel.WARN
    ),

    // 500
    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "예기치 않은 오류가 발생했습니다.", LogLevel.ERROR),
}
