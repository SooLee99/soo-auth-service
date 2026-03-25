package io.soo.springboot.core.domain.oauth2

import io.soo.springboot.core.enums.AuthProvider
import org.springframework.stereotype.Component

@Component
class OAuth2UserInfoParserRegistry(
    parsers: List<OAuth2UserInfoParser>,
) {
    private val parserByProvider: Map<AuthProvider, OAuth2UserInfoParser> =
        parsers.associateBy { it.provider }

    fun parse(provider: AuthProvider, attrs: Map<String, Any?>): OAuth2UserInfo {
        val parser = parserByProvider[provider] ?: error("unsupported provider: $provider")
        return parser.parse(attrs)
    }
}

