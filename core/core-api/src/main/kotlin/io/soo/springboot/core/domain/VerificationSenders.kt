package io.soo.springboot.core.domain

interface SmsSender {
    fun send(toPhoneNumber: String, message: String)
}

interface EmailSender {
    fun send(toEmail: String, subject: String, body: String)
}
