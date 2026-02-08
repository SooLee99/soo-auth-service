package io.soo.springboot.core.support.response

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import io.soo.springboot.core.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import java.time.LocalDateTime

@JsonPropertyOrder("result", "meta", "data", "error")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val result: ResultType,
    val meta: Meta,
    val data: T? = null,
    val error: Error? = null,
) {

    @JsonPropertyOrder("timestamp", "requestId", "durationMs", "request", "paging", "locale")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Meta(
        val timestamp: LocalDateTime,
        val requestId: String? = null,
        val durationMs: Long? = null,
        val request: Request,
        val paging: Paging? = null,
        val locale: String? = null,
    )

    @JsonPropertyOrder("path", "method", "query")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Request(
        val path: String?,
        val method: String?,
        val query: String?,
    )

    @JsonPropertyOrder("size", "number", "totalElements", "totalPages", "hasNext", "hasPrev")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Paging(
        val size: Int,
        val number: Int,
        val totalElements: Long,
        val totalPages: Int,
        val hasNext: Boolean,
        val hasPrev: Boolean,
    )

    @JsonPropertyOrder("code", "message", "fields")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Error(
        val code: String,
        val message: String,
        val fields: Any? = null, // validation errors / duplicate field 등 (민감정보 금지)
    )

    companion object {
        private fun meta(
            path: String?,
            method: String?,
            query: String?,
            timestamp: LocalDateTime = LocalDateTime.now(),
            requestId: String? = null,
            durationMs: Long? = null,
            locale: String? = null,
            paging: Paging? = null,
        ): Meta = Meta(
            timestamp = timestamp,
            requestId = requestId,
            durationMs = durationMs,
            request = Request(path, method, query),
            paging = paging,
            locale = locale,
        )

        fun meta(
            req: HttpServletRequest,
            timestamp: LocalDateTime = LocalDateTime.now(),
            requestId: String? = null,
            durationMs: Long? = null,
            locale: String? = null,
            paging: Paging? = null,
        ): Meta = meta(
            path = req.requestURI,
            method = req.method,
            query = req.queryString,
            timestamp = timestamp,
            requestId = requestId,
            durationMs = durationMs,
            locale = locale,
            paging = paging,
        )

        fun <S> success(
            req: HttpServletRequest,
            data: S? = null,
            requestId: String? = null,
            durationMs: Long? = null,
            locale: String? = null,
            paging: Paging? = null,
        ): ApiResponse<S> =
            ApiResponse(
                result = ResultType.SUCCESS,
                meta = meta(req, requestId = requestId, durationMs = durationMs, locale = locale, paging = paging),
                data = data,
                error = null,
            )

        fun <S> success(
            path: String?,
            method: String?,
            query: String?,
            data: S? = null,
            timestamp: LocalDateTime = LocalDateTime.now(),
            requestId: String? = null,
            durationMs: Long? = null,
            locale: String? = null,
            paging: Paging? = null,
        ): ApiResponse<S> =
            ApiResponse(
                result = ResultType.SUCCESS,
                meta = meta(path, method, query, timestamp, requestId, durationMs, locale, paging),
                data = data,
                error = null,
            )

        fun error(
            type: ErrorType,
            req: HttpServletRequest,
            fields: Any? = null,
            requestId: String? = null,
            durationMs: Long? = null,
            locale: String? = null,
            paging: Paging? = null,
        ): ApiResponse<Nothing> =
            ApiResponse(
                result = ResultType.ERROR,
                meta = meta(req, requestId = requestId, durationMs = durationMs, locale = locale, paging = paging),
                data = null,
                error = Error(
                    code = type.code.name,
                    message = type.message,
                    fields = fields,
                ),
            )

        fun error(
            type: ErrorType,
            message: String,
            req: HttpServletRequest,
            fields: Any? = null,
            requestId: String? = null,
            durationMs: Long? = null,
            locale: String? = null,
            paging: Paging? = null,
        ): ApiResponse<Nothing> =
            ApiResponse(
                result = ResultType.ERROR,
                meta = meta(req, requestId = requestId, durationMs = durationMs, locale = locale, paging = paging),
                data = null,
                error = Error(
                    code = type.code.name,
                    message = message,
                    fields = fields,
                ),
            )

        fun error(
            type: ErrorType,
            path: String?,
            method: String?,
            query: String?,
            timestamp: LocalDateTime = LocalDateTime.now(),
            fields: Any? = null,
            requestId: String? = null,
            durationMs: Long? = null,
            locale: String? = null,
            paging: Paging? = null,
        ): ApiResponse<Nothing> =
            ApiResponse(
                result = ResultType.ERROR,
                meta = meta(path, method, query, timestamp, requestId, durationMs, locale, paging),
                data = null,
                error = Error(
                    code = type.code.name,
                    message = type.message,
                    fields = fields,
                ),
            )

        fun paging(
            size: Int,
            number: Int,
            totalElements: Long,
            totalPages: Int,
            hasNext: Boolean,
            hasPrev: Boolean,
        ): Paging = Paging(size, number, totalElements, totalPages, hasNext, hasPrev)
    }
}
