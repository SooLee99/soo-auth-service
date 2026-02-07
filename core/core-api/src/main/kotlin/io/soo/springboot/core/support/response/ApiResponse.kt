package io.soo.springboot.core.support.response

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import io.soo.springboot.core.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import java.time.LocalDateTime

@JsonPropertyOrder(
    value = [
        "result",
        "code",
        "message",
        "timestamp",
        "path",
        "method",
        "query",
        "data",
    ]
)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val result: ResultType,
    val code: String? = null,
    val message: String? = null,
    val timestamp: LocalDateTime? = null,
    val path: String? = null,
    val method: String? = null,
    val query: String? = null,
    val data: T? = null,
) {
    companion object {
        // ✅ SUCCESS (요청 메타 포함)
        fun <S> success(req: HttpServletRequest, data: S? = null): ApiResponse<S> =
            ApiResponse(
                result = ResultType.SUCCESS,
                timestamp = LocalDateTime.now(),
                path = req.requestURI,
                method = req.method,
                query = req.queryString,
                data = data,
            )

        // ✅ SUCCESS (필터/핸들러 등에서 req 없이 메타를 직접 넣고 싶을 때)
        fun <S> success(
            path: String?,
            method: String?,
            query: String?,
            timestamp: LocalDateTime = LocalDateTime.now(),
            data: S? = null,
        ): ApiResponse<S> =
            ApiResponse(
                result = ResultType.SUCCESS,
                timestamp = timestamp,
                path = path,
                method = method,
                query = query,
                data = data,
            )

        // ✅ ERROR (ErrorType.message 사용) - req 버전
        fun error(
            type: ErrorType,
            req: HttpServletRequest,
            data: Any? = null,
        ): ApiResponse<Any> =
            ApiResponse(
                result = ResultType.ERROR,
                code = type.code.name,
                message = type.message,
                timestamp = LocalDateTime.now(),
                path = req.requestURI,
                method = req.method,
                query = req.queryString,
                data = data,
            )

        // ✅ ERROR (ErrorType.message 사용) - 메타 직접 주입 버전 (너가 쓰는 호출 형태)
        fun error(
            type: ErrorType,
            path: String?,
            method: String?,
            query: String?,
            timestamp: LocalDateTime = LocalDateTime.now(),
            data: Any? = null,
        ): ApiResponse<Any> =
            ApiResponse(
                result = ResultType.ERROR,
                code = type.code.name,
                message = type.message,
                timestamp = timestamp,
                path = path,
                method = method,
                query = query,
                data = data,
            )

        // ✅ ERROR (동적 메시지) - req 버전
        fun error(
            type: ErrorType,
            message: String,
            req: HttpServletRequest,
            data: Any? = null,
        ): ApiResponse<Any> =
            ApiResponse(
                result = ResultType.ERROR,
                code = type.code.name,
                message = message,
                timestamp = LocalDateTime.now(),
                path = req.requestURI,
                method = req.method,
                query = req.queryString,
                data = data,
            )

        // ✅ ERROR (동적 메시지) - 메타 직접 주입 버전
        fun error(
            type: ErrorType,
            message: String,
            path: String?,
            method: String?,
            query: String?,
            timestamp: LocalDateTime = LocalDateTime.now(),
            data: Any? = null,
        ): ApiResponse<Any> =
            ApiResponse(
                result = ResultType.ERROR,
                code = type.code.name,
                message = message,
                timestamp = timestamp,
                path = path,
                method = method,
                query = query,
                data = data,
            )
    }
}
