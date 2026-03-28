package io.soo.springboot.core.domain.local.phone.sms

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("solapi.sms")
class SolapiProps {
    var enabled: Boolean = false
    var apiKey: String = ""
    var apiSecret: String = ""
    var sendFrom: String = ""
    var apiDomain: String = "https://api.coolsms.co.kr"
}
