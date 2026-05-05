package io.soo.springboot.core.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.auth.method")
data class AuthMethodProperties(
    val email: EnabledProperty = EnabledProperty(),
    val id: EnabledProperty = EnabledProperty(),
    val sms: EnabledProperty = EnabledProperty(),
    val oauth2: EnabledProperty = EnabledProperty(),
    val admin: EnabledProperty = EnabledProperty(),
) {
    data class EnabledProperty(
        val enabled: Boolean = true,
    )
}
