package io.soo.springboot.core.api.controller.v1.response

data class AuthSessionStatusResponse(
    val authenticated: Boolean,
    val userId: Long? = null,
    val subject: String? = null,
)
