package io.soo.springboot.core.domain.local.phone.sms

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "solapi.sms", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class LogSmsSend : SmsSend {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun provider(): String = "LOG"

    override fun from(): String = "LOG"

    override fun send(to: String, text: String) {
        log.info("admin sms send. to={}, text={}", to, text)
    }
}
