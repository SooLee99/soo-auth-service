package io.soo.springboot.core.api.security.auth

import io.soo.springboot.core.domain.OAuth2AccountService
import io.soo.springboot.core.domain.OAuth2UserInfoExtractor
import io.soo.springboot.core.api.security.userdetails.UserPrincipal
import io.soo.springboot.core.enums.AuthProvider
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service

@Service
class DefaultUserIdResolver(
    private val oauth2AccountService: OAuth2AccountService,
) : UserIdResolver {

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

        // 3) OAuth2
        if (authentication is OAuth2AuthenticationToken) {
            val registrationId = authentication.authorizedClientRegistrationId // "kakao"/"naver"/"google"
            val provider = AuthProvider.valueOf(registrationId.uppercase())

            val oauth2User = authentication.principal as OAuth2User
            val info = OAuth2UserInfoExtractor.extract(provider, oauth2User.attributes)

            return oauth2AccountService.upsertAndGetUserId(info)
        }

        throw IllegalStateException("Cannot resolve userId: unsupported auth principal=${principal::class.java.name}")
    }
}