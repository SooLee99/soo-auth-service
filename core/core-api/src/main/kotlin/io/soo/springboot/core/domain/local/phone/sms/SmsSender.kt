package io.soo.springboot.core.domain.local.phone.sms

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class SmsSender(
    private val smsProperties: SmsProperties,
    private val objectMapper: ObjectMapper,
) {
    fun sendSms(message: SmsMessage): SmsResponse {
        val time = System.currentTimeMillis()
        val headers = buildHeaders(time)
        val body = buildBody(message)
        val httpRequest = HttpEntity(objectMapper.writeValueAsString(body), headers)
        val endpoint = "https://sens.apigw.ntruss.com/sms/v2/services/${smsProperties.serviceId}/messages"
        val restTemplate = RestTemplate()
        return restTemplate.postForObject(endpoint, httpRequest, SmsResponse::class.java) ?: SmsResponse()
    }

    private fun buildHeaders(time: Long): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set("x-ncp-apigw-timestamp", time.toString())
        headers.set("x-ncp-iam-access-key", smsProperties.accessKey)
        headers.set("x-ncp-apigw-signature-v2", makeSignature(time))
        return headers
    }

    private fun buildBody(message: SmsMessage): SmsRequest {
        return SmsRequest(
            type = "SMS",
            contentType = "COMM",
            countryCode = "82",
            from = smsProperties.sendFrom,
            content = "[soo-auth] verification",
            messages = listOf(message),
        )
    }

    fun makeSignature(time: Long): String {
        val method = "POST"
        val url = "/sms/v2/services/${smsProperties.serviceId}/messages"
        val accessKey = smsProperties.accessKey
        val secretKey = smsProperties.secretKey
        val message = "$method $url\n$time\n$accessKey"

        val signingKey = SecretKeySpec(secretKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(signingKey)
        val rawHmac = mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(rawHmac)
    }
}
