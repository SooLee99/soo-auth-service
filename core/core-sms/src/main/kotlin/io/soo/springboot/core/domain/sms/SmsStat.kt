package io.soo.springboot.core.domain.sms

data class SmsStat(
    val total: Long,
    val ok: Long,
    val fail: Long,
    val rate: Double,
)
