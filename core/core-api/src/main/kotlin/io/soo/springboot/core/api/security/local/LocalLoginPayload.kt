package io.soo.springboot.core.api.security.local

data class LocalLoginPayload(
    val email: String,
    val password: String,
)
