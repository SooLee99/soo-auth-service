package io.soo.springboot.core.domain

import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

@Component
class JavaMailEmailSender(
    private val mailSender: JavaMailSender,
    @Value("\${app.mail.from:}") private val from: String,
) : EmailSender {

    override fun send(toEmail: String, subject: String, body: String) {
        val msg = SimpleMailMessage()
        if (from.isNotBlank()) msg.from = from
        msg.setTo(toEmail)
        msg.subject = subject
        msg.text = body
        mailSender.send(msg)
    }
}
