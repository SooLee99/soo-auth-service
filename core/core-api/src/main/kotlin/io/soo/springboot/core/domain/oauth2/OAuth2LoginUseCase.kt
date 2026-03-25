package io.soo.springboot.core.domain.oauth2

import io.soo.springboot.core.enums.AuthProvider
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
class OAuth2LoginUseCase(
    private val oAuth2AccountService: OAuth2AccountService,
    private val oAuth2UserInfoParserRegistry: OAuth2UserInfoParserRegistry,
) {
    fun resolveOrCreateUserId(authentication: Authentication): Long {
        val oauth2Token = authentication as? OAuth2AuthenticationToken
            ?: throw IllegalStateException("OAuth2 authentication is required: ${authentication::class.java.name}")

        val provider = AuthProvider.valueOf(oauth2Token.authorizedClientRegistrationId.uppercase())
        val oauth2User = oauth2Token.principal as? OAuth2User
            ?: throw IllegalStateException("OAuth2 principal is required: ${oauth2Token.principal::class.java.name}")

        val info = oAuth2UserInfoParserRegistry.parse(provider, oauth2User.attributes)
        return oAuth2AccountService.upsertAndGetUserId(info)
    }
}
