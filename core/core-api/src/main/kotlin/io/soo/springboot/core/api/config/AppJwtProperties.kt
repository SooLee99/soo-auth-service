package io.soo.springboot.core.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.security.jwt")
data class AppJwtProperties(
    val issuer: String,
    val keyId: String = "jwt",
    val accessTtlSeconds: Long = 900,
    val refreshTtlSeconds: Long = 1209600,
    val keystore: Keystore,
) {
    data class Keystore(
        val location: String,
        val password: String,
        val alias: String = "jwt",
        val keyPassword: String? = null,
    )
}
