package io.soo.springboot.core.support.error

import org.springframework.validation.FieldError

data class FieldErrorDetail(
    val field: String,
    val rejectedValue: Any?,
    val reason: String?,
) {
    companion object {
        fun from(e: FieldError): FieldErrorDetail =
            FieldErrorDetail(
                field = e.field,
                rejectedValue = e.rejectedValue,
                reason = e.defaultMessage,
            )
    }
}
