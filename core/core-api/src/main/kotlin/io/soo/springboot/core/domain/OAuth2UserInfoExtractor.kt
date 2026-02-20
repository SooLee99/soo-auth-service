package io.soo.springboot.core.domain

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Gender
import java.time.Instant

object OAuth2UserInfoExtractor {

    fun extract(provider: AuthProvider, attrs: Map<String, Any?>): OAuth2UserInfo {
        return when (provider) {
            AuthProvider.GOOGLE -> extractGoogle(attrs)
            AuthProvider.KAKAO -> extractKakao(attrs)
            AuthProvider.NAVER -> extractNaver(attrs)
            else -> error("unsupported provider: $provider")
        }
    }

    // -----------------------------
    // Google (OIDC UserInfo 기반)
    // -----------------------------
    private fun extractGoogle(attrs: Map<String, Any?>): OAuth2UserInfo {
        val sub = attrs.str("sub") ?: attrs.str("id") ?: error("google: missing sub")

        val email = attrs.str("email")
        val emailVerified = attrs.bool("email_verified")
        val name = attrs.str("name")
        val nickname = attrs.str("preferred_username")
        val locale = attrs.str("locale")
        val picture = attrs.str("picture")

        val extra = mapOf(
            "given_name" to attrs.str("given_name"),
            "family_name" to attrs.str("family_name"),
            "hd" to attrs.str("hd"),
        ).filterValues { it != null }

        return OAuth2UserInfo(
            provider = AuthProvider.GOOGLE,
            providerUserId = sub,
            email = email,
            emailVerified = emailVerified,
            name = name,
            nickname = nickname,
            locale = locale,
            profileImageUrl = picture,
            thumbnailImageUrl = picture,
            extra = extra,
            raw = attrs,
        )
    }

    // -----------------------------
    // Kakao (/v2/user/me)
    // -----------------------------
    @Suppress("UNCHECKED_CAST")
    private fun extractKakao(attrs: Map<String, Any?>): OAuth2UserInfo {
        val id = attrs.anyStr("id", "user_id") ?: error("kakao: missing id")

        val connectedAt = attrs.str("connected_at")?.let { runCatching { Instant.parse(it) }.getOrNull() }

        val properties = attrs.map("properties")
        val kakaoAccount = attrs.map("kakao_account")
        val profileInAccount = kakaoAccount?.map("profile")

        val nickname =
            profileInAccount?.str("nickname")
                ?: properties?.str("nickname")

        val profileImageUrl =
            profileInAccount?.str("profile_image_url")
                ?: properties?.str("profile_image")

        val thumbnailImageUrl =
            profileInAccount?.str("thumbnail_image_url")
                ?: properties?.str("thumbnail_image")

        val email = kakaoAccount?.str("email")
        // 카카오는 email 관련 상태값도 함께 줌(동의/유효/검증 등)
        val emailVerified =
            kakaoAccount?.bool("is_email_verified")
                ?: kakaoAccount?.bool("email_verified")

        val gender = kakaoAccount?.str("gender")?.toGenderKakao()
        val ageRange = kakaoAccount?.str("age_range")
        val birthyear = kakaoAccount?.str("birthyear")

        // 카카오 birthday는 "MMDD" 형태가 올 수 있어 "MM-DD"로 정규화
        val birthday = kakaoAccount?.str("birthday")?.toBirthdayMMddOrMMdd()

        val phoneNumber = kakaoAccount?.str("phone_number")
        val phoneNumberE164 = null

        val name = kakaoAccount?.str("name")
        val locale = kakaoAccount?.str("locale")

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
            provider = AuthProvider.KAKAO,
            providerUserId = id,
            email = email,
            emailVerified = emailVerified,
            name = name,
            nickname = nickname,
            locale = locale,
            profileImageUrl = profileImageUrl,
            thumbnailImageUrl = thumbnailImageUrl,
            gender = gender,
            birthday = birthday,
            birthyear = birthyear,
            ageRange = ageRange,
            phoneNumber = phoneNumber,
            phoneNumberE164 = phoneNumberE164,
            connectedAt = connectedAt,
            extra = extra,
            raw = attrs,
        )
    }

    // -----------------------------
    // Naver (/v1/nid/me) -> response 하위
    // -----------------------------
    @Suppress("UNCHECKED_CAST")
    private fun extractNaver(attrs: Map<String, Any?>): OAuth2UserInfo {
        // 네이버 { resultcode, message, response: { ... } }
        val response = attrs.map("response") ?: error("naver: missing response")

        val id = response.str("id") ?: error("naver: missing response.id")

        val email = response.str("email")
        val nickname = response.str("nickname")
        val name = response.str("name")
        val profileImageUrl = response.str("profile_image")
        val thumbnailImageUrl = profileImageUrl // 별도 없으면 동일 사용

        val gender = response.str("gender")?.toGenderNaver()
        val birthday = response.str("birthday")?.toBirthdayMMddOrMMdd() // "MM-DD"
        val birthyear = response.str("birthyear")

        val ageRange = response.str("age")

        val phoneNumber = response.str("mobile")
        val phoneNumberE164 = response.str("mobile_e164")

        val extra = buildMap<String, Any?> {
            put("resultcode", attrs.str("resultcode"))
            put("message", attrs.str("message"))
        }.filterValues { it != null }

        return OAuth2UserInfo(
            provider = AuthProvider.NAVER,
            providerUserId = id,
            email = email,
            emailVerified = null,
            name = name,
            nickname = nickname,
            locale = null,
            profileImageUrl = profileImageUrl,
            thumbnailImageUrl = thumbnailImageUrl,
            gender = gender,
            birthday = birthday,
            birthyear = birthyear,
            ageRange = ageRange,
            phoneNumber = phoneNumber,
            phoneNumberE164 = phoneNumberE164,
            connectedAt = null,
            extra = extra,
            raw = attrs,
        )
    }

    // -----------------------------
    // Helpers
    // -----------------------------
    private fun Map<String, Any?>.str(key: String): String? =
        this[key]?.toString()?.takeIf { it.isNotBlank() }

    private fun Map<String, Any?>.anyStr(vararg keys: String): String? =
        keys.asSequence().mapNotNull { str(it) }.firstOrNull()

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.map(key: String): Map<String, Any?>? =
        (this[key] as? Map<*, *>)?.entries
            ?.associate { it.key.toString() to it.value }

    private fun Map<String, Any?>.bool(key: String): Boolean? =
        when (val v = this[key]) {
            is Boolean -> v
            is String -> v.equals("true", ignoreCase = true) || v == "Y"
            is Number -> v.toInt() != 0
            else -> null
        }

    private fun String.toBirthdayMMddOrMMdd(): String? {
        val s = this.trim()
        // "MM-DD"
        if (Regex("""^\d{2}-\d{2}$""").matches(s)) return s
        // "MMDD"
        if (Regex("""^\d{4}$""").matches(s)) return "${s.substring(0, 2)}-${s.substring(2, 4)}"
        return null
    }

    private fun String.toGenderKakao(): Gender =
        when (this.lowercase()) {
            "male" -> Gender.MALE
            "female" -> Gender.FEMALE
            else -> Gender.UNKNOWN
        }

    private fun String.toGenderNaver(): Gender =
        when (this.uppercase()) {
            "M" -> Gender.MALE
            "F" -> Gender.FEMALE
            else -> Gender.UNKNOWN
        }
}