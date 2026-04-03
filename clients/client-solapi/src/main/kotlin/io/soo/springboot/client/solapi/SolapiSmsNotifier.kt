package io.soo.springboot.client.solapi

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "solapi.sms", name = ["enabled"], havingValue = "true")
class SolapiSmsNotifier(
    private val sender: SolapiSmsSend,
) {
    fun sendCode(phoneNumber: String, code: String, expiresInSec: Long): SolapiNotificationResult {
        val text = "[soo] 인증번호 [$code] (유효 ${expiresInSec / 60}분)"
        sender.send(phoneNumber, text)
        return SolapiNotificationResult(
            to = phoneNumber,
            from = sender.from(),
            text = text,
            provider = sender.provider(),
        )
    }
}

data class SolapiNotificationResult(
    val to: String,
    val from: String,
    val text: String,
    val provider: String,
)
