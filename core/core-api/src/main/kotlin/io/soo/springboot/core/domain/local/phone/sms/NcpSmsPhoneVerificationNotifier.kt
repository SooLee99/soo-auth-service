package io.soo.springboot.core.domain.local.phone.sms

import io.soo.springboot.core.domain.local.phone.PhoneVerificationNotifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "ncp.sms", name = ["enabled"], havingValue = "true")
class NcpSmsPhoneVerificationNotifier(
    private val smsSender: SmsSender,
) : PhoneVerificationNotifier {
    override fun sendVerificationCode(phoneNumber: String, code: String, expiresInSec: Long) {
        val to = toDomesticNumber(phoneNumber)
        val content = "[soo-auth] 인증번호 [$code] (유효 ${expiresInSec / 60}분)"
        smsSender.sendSms(SmsMessage(to = to, content = content))
    }

    private fun toDomesticNumber(phoneNumber: String): String {
        val digits = phoneNumber.filter { it.isDigit() }
        return when {
            digits.startsWith("82") -> "0${digits.removePrefix("82")}"
            else -> digits
        }
    }
}
