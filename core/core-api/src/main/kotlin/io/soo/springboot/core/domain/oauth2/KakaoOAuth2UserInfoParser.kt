package io.soo.springboot.core.domain.oauth2

import io.soo.springboot.core.domain.OAuth2UserInfo
import io.soo.springboot.core.enums.AuthProvider
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class KakaoOAuth2UserInfoParser : OAuth2ParserSupport(), OAuth2UserInfoParser {
    override val provider: AuthProvider = AuthProvider.KAKAO

    override fun parse(attrs: Map<String, Any?>): OAuth2UserInfo {
        val id = attrs.anyStr("id", "user_id") ?: error("kakao: missing id")
        val connectedAt = attrs.str("connected_at")?.let { runCatching { Instant.parse(it) }.getOrNull() }

        val properties = attrs.map("properties")
        val kakaoAccount = attrs.map("kakao_account")
        val profileInAccount = kakaoAccount?.map("profile")

        val nickname = profileInAccount?.str("nickname") ?: properties?.str("nickname")
        val profileImageUrl = profileInAccount?.str("profile_image_url") ?: properties?.str("profile_image")
        val thumbnailImageUrl = profileInAccount?.str("thumbnail_image_url") ?: properties?.str("thumbnail_image")

        val extra = buildMap<String, Any?> {
            put("has_email", kakaoAccount?.bool("has_email"))
            put("email_needs_agreement", kakaoAccount?.bool("email_needs_agreement"))
            put("is_email_valid", kakaoAccount?.bool("is_email_valid"))
            put("has_phone_number", kakaoAccount?.bool("has_phone_number"))
            put("phone_number_needs_agreement", kakaoAccount?.bool("phone_number_needs_agreement"))
            put("has_gender", kakaoAccount?.bool("has_gender"))
            put("gender_needs_agreement", kakaoAccount?.bool("gender_needs_agreement"))
            put("has_age_range", kakaoAccount?.bool("has_age_range"))
            put("age_range_needs_agreement", kakaoAccount?.bool("age_range_needs_agreement"))
            put("has_birthday", kakaoAccount?.bool("has_birthday"))
            put("birthday_needs_agreement", kakaoAccount?.bool("birthday_needs_agreement"))
            put("birthday_type", kakaoAccount?.str("birthday_type"))
            put("has_birthyear", kakaoAccount?.bool("has_birthyear"))
            put("birthyear_needs_agreement", kakaoAccount?.bool("birthyear_needs_agreement"))
            put("profile_needs_agreement", kakaoAccount?.bool("profile_needs_agreement"))
            put("profile_image_needs_agreement", kakaoAccount?.bool("profile_image_needs_agreement"))
        }.filterValues { it != null }

        return OAuth2UserInfo(
            provider = provider,
            providerUserId = id,
            email = kakaoAccount?.str("email"),
            emailVerified = kakaoAccount?.bool("is_email_verified") ?: kakaoAccount?.bool("email_verified"),
            name = kakaoAccount?.str("name"),
            nickname = nickname,
            locale = kakaoAccount?.str("locale"),
            profileImageUrl = profileImageUrl,
            thumbnailImageUrl = thumbnailImageUrl,
            gender = kakaoAccount?.str("gender")?.toGenderKakao(),
            birthday = kakaoAccount?.str("birthday")?.toBirthdayMMddOrMMdd(),
            birthyear = kakaoAccount?.str("birthyear"),
            ageRange = kakaoAccount?.str("age_range"),
            phoneNumber = kakaoAccount?.str("phone_number"),
            phoneNumberE164 = null,
            connectedAt = connectedAt,
            extra = extra,
            raw = attrs,
        )
    }
}

