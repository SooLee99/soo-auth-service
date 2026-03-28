package io.soo.springboot.core.domain

data class SmsStat(
    val total: Long,
    val ok: Long,
    val fail: Long,
    val rate: Double,
)
