package io.soo.springboot.core.domain.local.phone.sms

interface SmsSend {
    fun provider(): String
    fun from(): String
    fun send(to: String, text: String)
}
