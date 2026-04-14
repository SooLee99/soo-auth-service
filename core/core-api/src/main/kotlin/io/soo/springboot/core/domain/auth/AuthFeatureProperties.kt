package io.soo.springboot.core.domain.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.auth.features")
data class AuthFeatureProperties(
    val email: Boolean = true,
    val phone: Boolean = true,
    val id: Boolean = true,
    val oauth2: Boolean = true,
)
