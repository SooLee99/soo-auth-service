package io.soo.springboot.core.api.security.token

import io.soo.springboot.core.api.security.userdetails.UserPrincipal
import io.soo.springboot.core.domain.phone.login.LocalIssuedTokens
import io.soo.springboot.core.domain.phone.login.LocalLoginTokenIssuer
import io.soo.springboot.storage.db.core.User
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Component

@Component
class AuthTokenLocalLoginTokenIssuer(
    private val authTokenManager: AuthTokenManager,
) : LocalLoginTokenIssuer {
    override fun issue(user: User, deviceId: String): LocalIssuedTokens {
        val principal = UserPrincipal(
            userId = user.id,
            email = user.email,
            passwordHash = null,
            role = user.role,
            provider = user.authProvider,
        )
        val authentication = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        val tokens = authTokenManager.issue(authentication, user.id, deviceId, user.authProvider)

        return LocalIssuedTokens(
            accessToken = tokens.accessToken,
            accessExpiresInSec = tokens.accessExpiresInSec,
            refreshToken = tokens.refreshToken,
            refreshExpiresInSec = tokens.refreshExpiresInSec,
        )
    }
}
