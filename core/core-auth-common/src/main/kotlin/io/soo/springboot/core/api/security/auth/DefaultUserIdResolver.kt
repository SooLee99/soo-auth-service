package io.soo.springboot.core.api.security.auth

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

        // 2) principal.getUserId() 지원 객체
        val userIdMethod = principal.javaClass.methods.firstOrNull {
            it.name == "getUserId" && it.parameterCount == 0
        }
        if (userIdMethod != null) {
            val value = userIdMethod.invoke(principal)
            if (value is Number) {
                return value.toLong()
            }
        }

        throw IllegalStateException("Cannot resolve userId: unsupported auth principal=${principal::class.java.name}")
    }
}
