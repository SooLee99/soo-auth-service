package io.soo.springboot.core.domain.local.phone.sms

data class SmsMessage(
    val to: String,
    val content: String,
)
