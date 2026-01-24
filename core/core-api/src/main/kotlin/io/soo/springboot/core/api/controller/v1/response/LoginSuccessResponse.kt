package io.soo.springboot.core.api.controller.v1.response

import java.time.LocalDateTime

data class LoginSuccessResponseDto(
    val sessionId: String,
    val userId: Long,
    val deviceId: String,
    val provider: String,
    val createdAt: LocalDateTime,
    val lastAccessedAt: LocalDateTime?,
    val oauth2: OAuth2UserInfoResponseDto,
    val token: TokenInfoResponseDto?,
)

data class OAuth2UserInfoResponseDto(
    val providerUserId: String?,
    val email: String?,
    val nickname: String?,
    val profileImageUrl: String?,
)

data class TokenInfoResponseDto(
    val tokenType: String,
    val expiresAt: String?,
    val scopes: List<String>,
)
