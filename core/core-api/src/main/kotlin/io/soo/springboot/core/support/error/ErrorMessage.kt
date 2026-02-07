package io.soo.springboot.core.support.error

import io.soo.springboot.core.support.response.ResultType
import java.time.LocalDateTime

data class ErrorContext(
    val path: String?,
    val method: String?,
    val query: String?,
    val timestamp: LocalDateTime,
)

data class ErrorResponse(
    val result: ResultType = ResultType.ERROR,
    val code: String,
    val message: String,
    val data: Any? = null,
    val context: ErrorContext,
) {
    companion object {
        fun of(type: ErrorType, context: ErrorContext, data: Any? = null): ErrorResponse =
            ErrorResponse(
                code = type.code.name,
                message = type.message,
                data = data,
                context = context,
            )
    }
}
