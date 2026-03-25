package io.soo.springboot.core.domain.admin.bootstrap

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.bootstrap.admin")
data class AdminBootstrapProperties(
    val enabled: Boolean = false,
    val username: String = "",
    val password: String = "",
    val allowedProfiles: List<String> = listOf("local", "local-dev"),
    val allowWeakPassword: Boolean = false,
)