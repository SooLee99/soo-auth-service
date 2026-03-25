package io.soo.springboot.core.domain.local.phone.sms

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("ncp.sms")
class SmsProperties {
    var enabled: Boolean = false
    var accessKey: String = ""
    var secretKey: String = ""
    var serviceId: String = ""
    var sendFrom: String = ""
}
