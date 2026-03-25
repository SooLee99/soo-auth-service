package io.soo.springboot.core.domain.local.phone.sms

import java.time.LocalDateTime

data class SmsResponse(
    val requestId: String? = null,
    val requestTime: LocalDateTime? = null,
    val statusCode: String? = null,
    val statusName: String? = null,
)
