package io.soo.springboot.core.api.config

import io.soo.springboot.core.domain.EmailSender
import io.soo.springboot.core.domain.SmsSender
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("local", "dev")
class DevSendersConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun smsSender(): SmsSender = object : SmsSender {
        override fun send(toPhoneNumber: String, message: String) {
            log.info("[DEV][SMS] to={} msg={}", toPhoneNumber, message)
        }
    }

    @Bean
    fun emailSender(): EmailSender = object : EmailSender {
        override fun send(toEmail: String, subject: String, body: String) {
            log.info("[DEV][EMAIL] to={} subject={} body={}", toEmail, subject, body)
        }
    }
}
