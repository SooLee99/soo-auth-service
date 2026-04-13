package io.soo.springboot.core.api.controller.v1.response

import java.time.Instant

data class LoginSuccessResponse(
    val result: String = "OK",
    val data: Data,
) {
    data class Data(
        val token: Token,
        val user: User,
    )

    data class Token(
        val accessToken: String?,
        val tokenType: String = "Bearer",
        val expiresIn: Long?,
        val issuedAt: Instant = Instant.now(),
        val refreshToken: String?,
        val refreshExpiresIn: Long?,
    )

    data class User(
        val id: Long,
        val provider: String?,
        val email: String?,
        val roles: List<String>,
    )
}

data class LogoutRequest(
    val refreshToken: String? = null,
    val logoutAll: Boolean = false,
)

data class AuthSessionStatusResponse(
    val authenticated: Boolean,
    val userId: Long? = null,
    val subject: String? = null,
)
