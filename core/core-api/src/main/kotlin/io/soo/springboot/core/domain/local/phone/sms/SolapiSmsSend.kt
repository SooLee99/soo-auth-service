package io.soo.springboot.core.domain.local.phone.sms

import com.solapi.sdk.SolapiClient
import com.solapi.sdk.message.model.Message
import com.solapi.sdk.message.service.DefaultMessageService
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "solapi.sms", name = ["enabled"], havingValue = "true")
class SolapiSmsSend(
    private val props: SolapiProps,
) : SmsSend {
    @Volatile
    private var messageService: DefaultMessageService? = null

    override fun provider(): String = "SOLAPI"

    override fun from(): String = props.sendFrom

    override fun send(to: String, text: String) {
        val message = Message().apply {
            from = props.sendFrom
            this.to = to
            this.text = text
        }

        try {
            messageService().send(message)
        } catch (e: Exception) {
            throw CoreException(
                ErrorType.DEFAULT_ERROR,
                mapOf(
                    "reason" to "SOLAPI_SEND_FAILED",
                    "detail" to (e.message ?: "unknown"),
                ),
            )
        }
    }

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
}
