package io.soo.springboot.core.api.security.response

object SecurityErrorFields {
    fun authRequired() = mapOf("reason" to "AUTH_REQUIRED")
    fun accessDenied() = mapOf("reason" to "ACCESS_DENIED")
    fun badCredentials() = mapOf("reason" to "INVALID_CREDENTIALS")
    fun accountDisabled() = mapOf("reason" to "ACCOUNT_DISABLED")
    fun loginDenied() = mapOf("reason" to "LOGIN_DENIED")
}
