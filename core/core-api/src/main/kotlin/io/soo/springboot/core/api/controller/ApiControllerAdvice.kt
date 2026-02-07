package io.soo.springboot.core.api.controller

import com.fasterxml.jackson.databind.exc.InvalidFormatException
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.core.support.error.FieldErrorDetail
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.persistence.EntityNotFoundException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException as ValidationConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.boot.logging.LogLevel
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import java.nio.file.AccessDeniedException as FileAccessDeniedException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.UUID

@RestControllerAdvice
class ApiControllerAdvice {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(CoreException::class)
    fun handleCoreException(e: CoreException, req: HttpServletRequest): ResponseEntity<ApiResponse<Any>> {
        val now = LocalDateTime.now()
        logByLevel(e.errorType.logLevel, "CoreException : ${e.message}", e)

        return ResponseEntity
            .status(e.errorType.status)
            .body(
                ApiResponse.error(
                    type = e.errorType,
                    path = req.requestURI,
                    method = req.method,
                    query = req.queryString,
                    timestamp = now,
                    data = e.data, // ✅ CoreException의 data는 그대로 루트 data로
                )
            )
    }

    // 400: JSON 바디 파싱 실패 (Enum/타입 변환 포함)
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(
        e: HttpMessageNotReadableException,
        req: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val now = LocalDateTime.now()
        val cause = e.cause

        if (cause is InvalidFormatException) {
            val targetType = cause.targetType
            val valueText = cause.value?.toString() ?: "null"

            // 1) Enum 변환 실패
            if (targetType.isEnum) {
                val enumType = targetType.simpleName
                val allowed = targetType.enumConstants.joinToString(", ") { it.toString() }
                val msg = "'$valueText'는 유효하지 않은 $enumType 값입니다. 사용 가능한 값: [$allowed]"

                return respond(
                    type = ErrorType.INVALID_REQUEST_BODY,
                    req = req,
                    userMessage = msg,
                    detail = e.message,
                    timestamp = now,
                )
            }

            // 2) 타입 변환 실패
            val msg = "'$valueText'는 ${targetType.simpleName} 타입으로 변환할 수 없습니다."
            return respond(
                type = ErrorType.INVALID_REQUEST_BODY,
                req = req,
                userMessage = msg,
                detail = e.message,
                timestamp = now,
            )
        }

        // 3) 일반 파싱 실패
        return respond(
            type = ErrorType.INVALID_REQUEST_BODY,
            req = req,
            userMessage = "요청 본문이 올바른 형식이 아닙니다. JSON 형식을 확인해주세요.",
            detail = e.message,
            timestamp = now,
        )
    }

    // 400: @RequestBody Validation 실패
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(
        e: MethodArgumentNotValidException,
        req: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val now = LocalDateTime.now()
        val errorDetails = e.bindingResult.fieldErrors.map { FieldErrorDetail.from(it) }

        return respond(
            type = ErrorType.INVALID_INPUT_VALUE,
            req = req,
            userMessage = "입력값이 올바르지 않습니다.",
            detail = e.message,
            timestamp = now,
            extra = mapOf("errors" to errorDetails),
        )
    }

    // 400: 쿼리/폼 바인딩 실패
    @ExceptionHandler(BindException::class)
    fun handleBindException(e: BindException, req: HttpServletRequest): ResponseEntity<ApiResponse<Any>> {
        val now = LocalDateTime.now()
        val errorDetails = e.bindingResult.fieldErrors.map { FieldErrorDetail.from(it) }

        return respond(
            type = ErrorType.INVALID_PARAMETER,
            req = req,
            userMessage = "요청 파라미터 바인딩에 실패했습니다. 입력값을 확인해 주세요.",
            detail = e.message,
            timestamp = now,
            extra = mapOf("errors" to errorDetails),
        )
    }

    // 400: @RequestParam/@PathVariable 타입 미스매치
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatchException(
        ex: MethodArgumentTypeMismatchException,
        req: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val now = LocalDateTime.now()

        val paramName = ex.name
        val rejectedVal = ex.value
        val required = ex.requiredType
        val valueText = rejectedVal?.toString() ?: "null"

        val msg = when {
            required == null ->
                "파라미터 '$paramName': '$valueText'는 올바른 타입으로 변환할 수 없습니다."

            required == LocalDate::class.java ->
                "파라미터 '$paramName': '$valueText' 는 올바른 날짜가 아닙니다. yyyy-MM-dd 형식으로 입력해 주세요."

            required == Int::class.java || required == Integer.TYPE ||
                    required == Long::class.java || required == java.lang.Long.TYPE ->
                "파라미터 '$paramName': '$valueText' 는 정수로 변환할 수 없습니다."

            required == Double::class.java || required == java.lang.Double.TYPE ||
                    required == Float::class.java || required == java.lang.Float.TYPE ->
                "파라미터 '$paramName': '$valueText' 는 숫자(실수)로 변환할 수 없습니다."

            required == Boolean::class.java || required == java.lang.Boolean.TYPE ->
                "파라미터 '$paramName': '$valueText' 는 true/false 값이 아닙니다."

            required == UUID::class.java ->
                "파라미터 '$paramName': '$valueText' 는 UUID 형식이 아닙니다. 예) 123e4567-e89b-12d3-a456-426614174000"

            required.isEnum -> {
                val allowed = required.enumConstants.joinToString(", ") { it.toString() }
                "파라미터 '$paramName': '$valueText' 는 유효하지 않은 값입니다. 사용 가능한 값: [$allowed]"
            }

            else ->
                "파라미터 '$paramName': '$valueText' 는 ${required.simpleName} 타입으로 변환할 수 없습니다."
        }

        return respond(
            type = ErrorType.INVALID_PARAMETER,
            req = req,
            userMessage = msg,
            detail = ex.message,
            timestamp = now,
        )
    }

    // 400: @Validated 메서드 파라미터 검증 실패
    @ExceptionHandler(ValidationConstraintViolationException::class)
    fun handleConstraintViolationException(
        e: ValidationConstraintViolationException,
        req: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val now = LocalDateTime.now()
        val errors = e.constraintViolations.map {
            mapOf(
                "path" to it.propertyPath.toString(),
                "invalid" to it.invalidValue,
                "reason" to it.message,
            )
        }

        return respond(
            type = ErrorType.INVALID_PARAMETER,
            req = req,
            userMessage = "요청 파라미터가 올바르지 않습니다.",
            detail = e.message,
            timestamp = now,
            extra = mapOf("errors" to errors),
        )
    }

    // 405: 메서드 미지원
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleHttpRequestMethodNotSupportedException(
        e: HttpRequestMethodNotSupportedException,
        req: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val now = LocalDateTime.now()
        val supported = e.supportedHttpMethods
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { it.name() }
            ?: "없음"

        val msg = "지원하지 않는 HTTP 메서드입니다. 요청: ${e.method} / 허용: [$supported]"

        return respond(
            type = ErrorType.METHOD_NOT_ALLOWED,
            req = req,
            userMessage = msg,
            detail = e.message,
            timestamp = now,
        )
    }

    // 404: 핸들러(매핑) 없음
    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNoHandlerFoundException(
        e: NoHandlerFoundException,
        req: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val now = LocalDateTime.now()
        val msg = "요청하신 API를 찾을 수 없습니다. (${e.httpMethod} ${e.requestURL})"

        return respond(
            type = ErrorType.NOT_FOUND,
            req = req,
            userMessage = msg,
            detail = e.message,
            timestamp = now,
        )
    }

    // 415: Content-Type 문제
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleHttpMediaTypeNotSupported(
        e: HttpMediaTypeNotSupportedException,
        req: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val now = LocalDateTime.now()
        val supported = e.supportedMediaTypes.joinToString(", ")
        val msg = "지원하지 않는 Content-Type 입니다. 요청: ${e.contentType} / 허용: [$supported]"

        return respond(
            type = ErrorType.UNSUPPORTED_MEDIA_TYPE,
            req = req,
            userMessage = msg,
            detail = e.message,
            timestamp = now,
        )
    }

    // 406: Accept 문제
    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun handleHttpMediaTypeNotAcceptable(
        e: HttpMediaTypeNotAcceptableException,
        req: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val now = LocalDateTime.now()
        val supported = e.supportedMediaTypes.joinToString(", ")
        val msg = "요청한 응답 타입(Accept)을 지원하지 않습니다. 허용: [$supported]"

        return respond(
            type = ErrorType.NOT_ACCEPTABLE,
            req = req,
            userMessage = msg,
            detail = e.message,
            timestamp = now,
        )
    }

    // 413: 업로드 용량 초과
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSizeExceeded(
        e: MaxUploadSizeExceededException,
        req: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val now = LocalDateTime.now()
        return respond(
            type = ErrorType.PAYLOAD_TOO_LARGE,
            req = req,
            userMessage = "업로드 용량이 너무 큽니다.",
            detail = e.message,
            timestamp = now,
        )
    }

    // 409: DB 제약조건/유니크 키 등 충돌
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(
        e: DataIntegrityViolationException,
        req: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val now = LocalDateTime.now()
        val root = e.mostSpecificCause.message
        return respond(
            type = ErrorType.CONFLICT,
            req = req,
            userMessage = "데이터 제약조건 위반으로 요청을 처리할 수 없습니다.",
            detail = root ?: e.message,
            timestamp = now,
        )
    }

    // 409: 낙관적 락 충돌 (동시 수정)
    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLock(
        e: OptimisticLockingFailureException,
        req: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val now = LocalDateTime.now()
        return respond(
            type = ErrorType.CONFLICT,
            req = req,
            userMessage = "리소스가 이미 변경되었습니다. 새로고침 후 다시 시도해 주세요.",
            detail = e.message,
            timestamp = now,
        )
    }

    // 404: 엔티티 없음
    @ExceptionHandler(EntityNotFoundException::class)
    fun handleEntityNotFound(
        e: EntityNotFoundException,
        req: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val now = LocalDateTime.now()
        return respond(
            type = ErrorType.NOT_FOUND,
            req = req,
            userMessage = e.message ?: "요청하신 리소스를 찾을 수 없습니다.",
            detail = e.message,
            timestamp = now,
        )
    }

    // 파일 시스템 권한 예외
    @ExceptionHandler(FileAccessDeniedException::class)
    fun handleFileAccessDenied(
        e: FileAccessDeniedException,
        req: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val now = LocalDateTime.now()
        return respond(
            type = ErrorType.FORBIDDEN,
            req = req,
            userMessage = "파일 접근 권한이 없습니다.",
            detail = e.message,
            timestamp = now,
        )
    }

    @ExceptionHandler(DateTimeParseException::class)
    fun handleDateTimeParseException(e: DateTimeParseException, req: HttpServletRequest): ResponseEntity<ApiResponse<Any>> {
        val now = LocalDateTime.now()
        val msg = "날짜 형식이 올바르지 않습니다. 입력값: '${e.parsedString}'"
        return respond(ErrorType.INVALID_PARAMETER, req, msg, e.message, now)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException, req: HttpServletRequest): ResponseEntity<ApiResponse<Any>> {
        val now = LocalDateTime.now()
        val msg = e.message?.takeIf { it.isNotBlank() } ?: "잘못된 인자가 전달되었습니다."
        return respond(ErrorType.INVALID_PARAMETER, req, msg, e.message, now)
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(e: IllegalStateException, req: HttpServletRequest): ResponseEntity<ApiResponse<Any>> {
        val now = LocalDateTime.now()
        return respond(ErrorType.INVALID_PARAMETER, req, "현재 상태에서 수행할 수 없는 요청입니다.", e.message, now)
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingServletRequestParameter(
        e: MissingServletRequestParameterException,
        req: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val now = LocalDateTime.now()
        val msg = "필수 파라미터 '${e.parameterName}'(${e.parameterType})이(가) 누락되었습니다."
        return respond(ErrorType.INVALID_PARAMETER, req, msg, e.message, now)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception, req: HttpServletRequest): ResponseEntity<ApiResponse<Any>> {
        log.error("Exception : {}", e.message, e)
        val now = LocalDateTime.now()
        return respond(
            type = ErrorType.DEFAULT_ERROR,
            req = req,
            userMessage = "서버에서 처리 중 오류가 발생했습니다.",
            detail = e.message,
            timestamp = now,
        )
    }

    private fun respond(
        type: ErrorType,
        req: HttpServletRequest,
        userMessage: String,
        detail: String?,
        timestamp: LocalDateTime,
        extra: Map<String, Any?> = emptyMap(),
    ): ResponseEntity<ApiResponse<Any>> {
        val data = linkedMapOf<String, Any?>(
            "detail" to detail,
        ).apply { putAll(extra) }
            .filterValues { it != null }

        return ResponseEntity
            .status(type.status)
            .body(
                ApiResponse.error(
                    type = type,
                    message = userMessage,
                    path = req.requestURI,
                    method = req.method,
                    query = req.queryString,
                    timestamp = timestamp,
                    data = data.takeIf { it.isNotEmpty() },
                )
            )
    }

    private fun logByLevel(level: LogLevel, msg: String, t: Throwable?) {
        when (level) {
            LogLevel.ERROR -> if (t != null) log.error(msg, t) else log.error(msg)
            LogLevel.WARN -> if (t != null) log.warn(msg, t) else log.warn(msg)
            else -> if (t != null) log.info(msg, t) else log.info(msg)
        }
    }
}
