package io.soo.springboot.core.domain.local.login

import io.soo.springboot.storage.db.core.User

data class LocalIssuedTokens(
    val accessToken: String,
    val accessExpiresInSec: Long,
    val refreshToken: String,
    val refreshExpiresInSec: Long,
)

interface LocalLoginTokenIssuer {
    fun issue(user: User, deviceId: String): LocalIssuedTokens
}
