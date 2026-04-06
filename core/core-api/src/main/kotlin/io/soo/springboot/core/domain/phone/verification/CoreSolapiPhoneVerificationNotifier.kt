package io.soo.springboot.core.domain.phone.verification

import io.soo.springboot.client.solapi.SolapiSmsNotifier
import io.soo.springboot.core.domain.sms.SmsAdminService
import io.soo.springboot.core.domain.phone.verification.PhoneVerificationNotifier
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "solapi.sms", name = ["enabled"], havingValue = "true")
class CoreSolapiPhoneVerificationNotifier(
    private val notifier: SolapiSmsNotifier,
    private val smsAdminService: SmsAdminService,
) : PhoneVerificationNotifier {
    override fun sendCode(phoneNumber: String, code: String, expiresInSec: Long) {
        try {
            val sent = notifier.sendCode(phoneNumber, code, expiresInSec)
            smsAdminService.recordSuccessLog(sent.to, sent.from, sent.text, sent.provider)
        } catch (e: Exception) {
            val text = "[soo] 인증번호 [$code] (유효 ${expiresInSec / 60}분)"
            val detail = e.message ?: "unknown"
            smsAdminService.recordFailureLog(phoneNumber, "SOLAPI", text, "SOLAPI", null, detail)
            throw CoreException(
                ErrorType.DEFAULT_ERROR,
                mapOf(
                    "reason" to "SOLAPI_SEND_FAILED",
                    "detail" to detail,
                ),
            )
        }
    }
}
