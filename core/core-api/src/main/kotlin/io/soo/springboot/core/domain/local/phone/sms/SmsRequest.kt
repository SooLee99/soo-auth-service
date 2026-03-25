package io.soo.springboot.core.domain.local.phone.sms

data class SmsRequest(
    val type: String,
    val contentType: String,
    val countryCode: String,
    val from: String,
    val content: String,
    val messages: List<SmsMessage>,
)
