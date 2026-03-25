package io.soo.springboot.core.domain.oauth2

import io.soo.springboot.core.enums.AuthProvider
import org.springframework.stereotype.Component

@Component
class NaverOAuth2UserInfoParser : OAuth2ParserSupport(), OAuth2UserInfoParser {
    override val provider: AuthProvider = AuthProvider.NAVER

    override fun parse(attrs: Map<String, Any?>): OAuth2UserInfo {
        val response = attrs.map("response") ?: error("naver: missing response")
        val id = response.str("id") ?: error("naver: missing response.id")
        val profileImageUrl = response.str("profile_image")

        val extra = buildMap<String, Any?> {
            put("resultcode", attrs.str("resultcode"))
            put("message", attrs.str("message"))
        }.filterValues { it != null }

        return OAuth2UserInfo(
            provider = provider,
            providerUserId = id,
            email = response.str("email"),
            emailVerified = null,
            name = response.str("name"),
            nickname = response.str("nickname"),
            locale = null,
            profileImageUrl = profileImageUrl,
            thumbnailImageUrl = profileImageUrl,
            gender = response.str("gender")?.toGenderNaver(),
            birthday = response.str("birthday")?.toBirthdayMMddOrMMdd(),
            birthyear = response.str("birthyear"),
            ageRange = response.str("age"),
            phoneNumber = response.str("mobile"),
            phoneNumberE164 = response.str("mobile_e164"),
            connectedAt = null,
            extra = extra,
            raw = attrs,
        )
    }
}

