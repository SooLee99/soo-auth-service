package io.soo.springboot.core.domain.local.phone

import io.soo.springboot.core.domain.SmsAdmin
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "solapi.sms", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class LogPhoneNotifier(
    private val smsAdmin: SmsAdmin,
) : PhoneNotifier {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun sendCode(phoneNumber: String, code: String, expiresInSec: Long) {
        val text = "[soo-auth] 인증번호 [$code] (유효 ${expiresInSec / 60}분)"
        log.info(
            "phone verification code issued. phone={}, code={}, expiresInSec={}",
            phoneNumber,
            code,
            expiresInSec,
        )
        smsAdmin.ok(
            smsTo = phoneNumber,
            smsFrom = "LOG",
            smsText = text,
            provider = "LOG",
        )
    }
}
