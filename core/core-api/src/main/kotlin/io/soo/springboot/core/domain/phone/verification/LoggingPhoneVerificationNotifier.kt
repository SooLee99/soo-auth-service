package io.soo.springboot.core.domain.phone.verification

import io.soo.springboot.core.domain.sms.SmsAdminService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "solapi.sms", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class LoggingPhoneVerificationNotifier(
    private val smsAdminService: SmsAdminService,
) : PhoneVerificationNotifier {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun sendCode(phoneNumber: String, code: String, expiresInSec: Long) {
        val text = "[soo-auth] 인증번호 [$code] (유효 ${expiresInSec / 60}분)"
        log.info(
            "phone verification code issued. phone={}, code={}, expiresInSec={}",
            phoneNumber,
            code,
            expiresInSec,
        )
        smsAdminService.recordSuccessLog(
            smsTo = phoneNumber,
            smsFrom = "LOG",
            smsText = text,
            provider = "LOG",
        )
    }
}
