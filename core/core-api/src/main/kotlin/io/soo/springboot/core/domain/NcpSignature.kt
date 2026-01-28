package io.soo.springboot.core.domain

import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object NcpSignature {
    fun makeSignature(
        method: String,
        urlPathWithQuery: String, // 예: /sms/v2/services/{serviceId}/messages
        timestampMillis: String,
        accessKey: String,
        secretKey: String,
    ): String {
        val message = buildString {
            append(method)
            append(" ")
            append(urlPathWithQuery)
            append("\n")
            append(timestampMillis)
            append("\n")
            append(accessKey)
        }

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val rawHmac = mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(rawHmac)
    }
}
