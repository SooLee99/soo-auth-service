package io.soo.springboot.core.support.error

import io.soo.springboot.core.support.response.ResultType
import java.time.LocalDateTime

data class RequestMeta(
    val path: String?,
    val method: String?,
    val query: String?,
)

data class ErrorContext(
    val timestamp: LocalDateTime,
    val requestId: String? = null,
    val durationMs: Long? = null,
    val request: RequestMeta,
    val paging: PagingMeta? = null,
    val locale: String? = null,
)

data class PagingMeta(
    val size: Int,
    val number: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrev: Boolean,
)

data class ErrorBody(
    val code: String,
    val message: String,
    val fields: Any? = null,
)

data class ErrorResponse(
    val result: ResultType = ResultType.ERROR,
    val meta: ErrorContext,
    val data: Any? = null,   // 성공 payload가 아니므로 ERROR에서는 보통 null 유지
    val error: ErrorBody,
) {
    companion object {
        fun of(type: ErrorType, meta: ErrorContext, fields: Any? = null): ErrorResponse =
            ErrorResponse(
                meta = meta,
                data = null,
                error = ErrorBody(
                    code = type.code.name,
                    message = type.message,
                    fields = fields,
                )
            )

        fun of(type: ErrorType, message: String, meta: ErrorContext, fields: Any? = null): ErrorResponse =
            ErrorResponse(
                meta = meta,
                data = null,
                error = ErrorBody(
                    code = type.code.name,
                    message = message,
                    fields = fields,
                )
            )
    }
}
