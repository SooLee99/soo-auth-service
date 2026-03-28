package io.soo.springboot.core.domain.oauth2

import io.soo.springboot.core.enums.AuthProvider
import org.springframework.stereotype.Component

@Component
class OAuth2ParserRegistry(
    parsers: List<OAuth2Parser>,
) {
    private val parserByProvider: Map<AuthProvider, OAuth2Parser> =
        parsers.associateBy { it.provider }

    fun parse(provider: AuthProvider, attrs: Map<String, Any?>): OAuth2Profile {
        val parser = parserByProvider[provider] ?: error("unsupported provider: $provider")
        return parser.parse(attrs)
    }
}

