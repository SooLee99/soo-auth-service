package io.soo.springboot.core.domain.local.phone

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "ncp.sms", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class LoggingPhoneVerificationNotifier : PhoneVerificationNotifier {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun sendVerificationCode(phoneNumber: String, code: String, expiresInSec: Long) {
        log.info(
            "phone verification code issued. phone={}, code={}, expiresInSec={}",
            phoneNumber,
            code,
            expiresInSec,
        )
    }
}
