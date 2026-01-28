package io.soo.springboot.core.domain

import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class NaverSensSmsSender(
    private val props: NaverSensSmsProperties,
    restClientBuilder: RestClient.Builder,
) : SmsSender {

    private val client: RestClient = restClientBuilder
        .baseUrl(props.baseUrl)
        .build()

    data class SmsMessage(
        val to: String,
        val subject: String? = null,
        val content: String? = null,
    )

    data class SmsSendRequest(
        val type: String,                   // SMS | LMS | MMS
        val contentType: String? = null,    // COMM | AD
        val countryCode: String? = null,    // default 82
        val from: String,                   // 사전 등록 발신번호
        val subject: String? = null,        // LMS/MMS
        val content: String,                // 기본 메시지
        val messages: List<SmsMessage>,
    )

    override fun send(toPhoneNumber: String, message: String) {
        require(props.serviceId.isNotBlank()) { "ncp.sens.sms.service-id is required" }
        require(props.accessKey.isNotBlank()) { "ncp.sens.sms.access-key is required" }
        require(props.secretKey.isNotBlank()) { "ncp.sens.sms.secret-key is required" }
        require(props.from.isNotBlank()) { "ncp.sens.sms.from is required" }

        val to = toPhoneNumber.replace("-", "").replace(" ", "")
        val path = "/sms/v2/services/${props.serviceId}/messages"
        val timestamp = System.currentTimeMillis().toString()

        val signature = NcpSignature.makeSignature(
            method = "POST",
            urlPathWithQuery = path,
            timestampMillis = timestamp,
            accessKey = props.accessKey,
            secretKey = props.secretKey,
        )

        val body = SmsSendRequest(
            type = props.type,
            contentType = props.contentType,
            countryCode = props.countryCode,
            from = props.from,
            content = message,
            messages = listOf(SmsMessage(to = to)),
        )

        client.post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-ncp-apigw-timestamp", timestamp)
            .header("x-ncp-iam-access-key", props.accessKey)
            .header("x-ncp-apigw-signature-v2", signature)
            .body(body)
            .retrieve()
            .toBodilessEntity()
    }
}
