package io.soo.springboot.core.domain.oauth2

import io.soo.springboot.core.enums.AuthProvider
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
class OAuth2Login(
    private val account: OAuth2AccountService,
    private val parsers: OAuth2ParserRegistry,
) {
    fun userId(authentication: Authentication): Long {
        val token = authentication as? OAuth2AuthenticationToken
            ?: throw IllegalStateException("OAuth2 authentication is required: ${authentication::class.java.name}")
        val provider = AuthProvider.valueOf(token.authorizedClientRegistrationId.uppercase())
        val user = token.principal as? OAuth2User
            ?: throw IllegalStateException("OAuth2 principal is required: ${token.principal::class.java.name}")
        val info = parsers.parse(provider, user.attributes)
        return account.signUp(info)
    }
}
