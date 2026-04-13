package io.soo.springboot.core.domain.sms

interface SmsSender {
    fun provider(): String
    fun from(): String
    fun send(to: String, text: String)
}
