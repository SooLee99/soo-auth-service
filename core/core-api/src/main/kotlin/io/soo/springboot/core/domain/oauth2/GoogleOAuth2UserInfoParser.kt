package io.soo.springboot.core.domain.oauth2

import io.soo.springboot.core.domain.OAuth2UserInfo
import io.soo.springboot.core.enums.AuthProvider
import org.springframework.stereotype.Component

@Component
class GoogleOAuth2UserInfoParser : OAuth2ParserSupport(), OAuth2UserInfoParser {
    override val provider: AuthProvider = AuthProvider.GOOGLE

    override fun parse(attrs: Map<String, Any?>): OAuth2UserInfo {
        val sub = attrs.str("sub") ?: attrs.str("id") ?: error("google: missing sub")

        val extra = mapOf(
            "given_name" to attrs.str("given_name"),
            "family_name" to attrs.str("family_name"),
            "hd" to attrs.str("hd"),
        ).filterValues { it != null }

        return OAuth2UserInfo(
            provider = provider,
            providerUserId = sub,
            email = attrs.str("email"),
            emailVerified = attrs.bool("email_verified"),
            name = attrs.str("name"),
            nickname = attrs.str("preferred_username"),
            locale = attrs.str("locale"),
            profileImageUrl = attrs.str("picture"),
            thumbnailImageUrl = attrs.str("picture"),
            extra = extra,
            raw = attrs,
        )
    }
}

