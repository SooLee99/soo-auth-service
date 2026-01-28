package io.soo.springboot.core.domain
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ncp.sens.sms")
data class NaverSensSmsProperties(
    /** 일반: https://sens.apigw.ntruss.com
     *  금융: https://sens.apigw.fin-ntruss.com
     */
    var baseUrl: String = "https://sens.apigw.ntruss.com",

    /** SENS 프로젝트의 serviceId */
    var serviceId: String = "",

    /** NCP Access Key / Secret Key */
    var accessKey: String = "",
    var secretKey: String = "",

    /** 사전 등록된 발신번호(from) */
    var from: String = "",

    /** 기본값: 한국 82 */
    var countryCode: String = "82",

    /** SMS/LMS/MMS 중 OTP는 보통 SMS */
    var type: String = "SMS",

    /** COMM(일반) / AD(광고) */
    var contentType: String = "COMM",
)
