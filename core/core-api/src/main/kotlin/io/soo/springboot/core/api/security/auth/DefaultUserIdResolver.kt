package io.soo.springboot.core.api.security.auth

import io.soo.springboot.core.api.security.userdetails.UserPrincipal
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service

@Service
class DefaultUserIdResolver : UserIdResolver {

    override fun resolve(authentication: Authentication): Long {
        val principal = authentication.principal

        // 1) JWT
        if (principal is Jwt) {
            val uid = principal.claims["uid"] as? Number
                ?: throw IllegalStateException("Cannot resolve userId: missing uid claim")
            return uid.toLong()
        }

        // 2) 내부 UserPrincipal
        if (principal is UserPrincipal) {
            return principal.userId
        }

        throw IllegalStateException("Cannot resolve userId: unsupported auth principal=${principal::class.java.name}")
    }
}
