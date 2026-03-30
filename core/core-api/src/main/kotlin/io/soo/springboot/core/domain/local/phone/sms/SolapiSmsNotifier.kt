package io.soo.springboot.core.domain.local.phone.sms

import com.solapi.sdk.SolapiClient
import com.solapi.sdk.message.model.Message
import com.solapi.sdk.message.service.DefaultMessageService
import io.soo.springboot.core.domain.SmsAdminService
import io.soo.springboot.core.domain.local.phone.PhoneVerificationNotifier
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "solapi.sms", name = ["enabled"], havingValue = "true")
class SolapiSmsNotifier(
    private val props: SolapiProps,
    private val smsAdminService: SmsAdminService,
) : PhoneVerificationNotifier {
    @Volatile
    private var messageService: DefaultMessageService? = null

    private fun messageService(): DefaultMessageService {
        messageService?.let { return it }
        synchronized(this) {
            messageService?.let { return it }
            if (props.apiKey.isBlank() || props.apiSecret.isBlank() || props.sendFrom.isBlank()) {
                throw CoreException(ErrorType.INVALID_REQUEST, mapOf("reason" to "SOLAPI_CONFIG_MISSING"))
            }
            val created = SolapiClient.createInstance(props.apiKey, props.apiSecret)
            messageService = created
            return created
        }
    }

    override fun sendCode(phoneNumber: String, code: String, expiresInSec: Long) {
        val text = "[soo] 인증번호 [$code] (유효 ${expiresInSec / 60}분)"
        val message = Message().apply {
            from = props.sendFrom
            to = phoneNumber
            this.text = text
        }

        try {
            messageService().send(message)
            smsAdminService.ok(phoneNumber, props.sendFrom, text, "SOLAPI")
        } catch (e: Exception) {
            val detail = e.message ?: "unknown"
            smsAdminService.fail(phoneNumber, props.sendFrom, text, "SOLAPI", null, detail)
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
