package io.soo.springboot.core.domain.local.phone.sms.adapter

import io.soo.springboot.client.solapi.SolapiSmsSend
import io.soo.springboot.core.domain.local.phone.sms.SmsSend
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "solapi.sms", name = ["enabled"], havingValue = "true")
class CoreSolapiSmsSendAdapter(
    private val sender: SolapiSmsSend,
) : SmsSend {
    override fun provider(): String = sender.provider()

    override fun from(): String = sender.from()

    override fun send(to: String, text: String) {
        try {
            sender.send(to, text)
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
}
