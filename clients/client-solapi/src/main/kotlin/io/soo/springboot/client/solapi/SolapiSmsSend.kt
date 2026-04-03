package io.soo.springboot.client.solapi

import com.solapi.sdk.SolapiClient
import com.solapi.sdk.message.model.Message
import com.solapi.sdk.message.service.DefaultMessageService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "solapi.sms", name = ["enabled"], havingValue = "true")
class SolapiSmsSend(
    private val props: SolapiProps,
) {
    @Volatile
    private var messageService: DefaultMessageService? = null

    fun provider(): String = "SOLAPI"

    fun from(): String = props.sendFrom

    fun send(to: String, text: String) {
        val message = Message().apply {
            from = props.sendFrom
            this.to = to
            this.text = text
        }

        messageService().send(message)
    }

    private fun messageService(): DefaultMessageService {
        messageService?.let { return it }
        synchronized(this) {
            messageService?.let { return it }
            if (props.apiKey.isBlank() || props.apiSecret.isBlank() || props.sendFrom.isBlank()) {
                throw IllegalStateException("SOLAPI_CONFIG_MISSING")
            }
            val created = SolapiClient.createInstance(props.apiKey, props.apiSecret)
            messageService = created
            return created
        }
    }
}
