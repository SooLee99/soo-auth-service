package io.soo.springboot.core.domain.oauth2

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.core.domain.UserStatusPolicy
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.storage.db.core.User
import io.soo.springboot.storage.db.core.UserRepository
import io.soo.springboot.core.enums.Gender
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class OAuth2UserInfo(
    val provider: AuthProvider,
    val providerUserId: String,

    // 공통
    val email: String? = null,
    val emailVerified: Boolean? = null,
    val name: String? = null,
    val nickname: String? = null,
    val locale: String? = null,

    // 프로필 이미지
    val profileImageUrl: String? = null,
    val thumbnailImageUrl: String? = null,

    // 선택 정보 (동의/제공 여부에 따라 null 가능)
    val gender: Gender? = null,
    val birthday: String? = null,   // "MM-DD" 로 정규화
    val birthyear: String? = null,  // "YYYY"
    val ageRange: String? = null,   // "20~29" or "20-29" 등 provider별 원본

    val phoneNumber: String? = null,
    val phoneNumberE164: String? = null,

    // provider별 메타
    val connectedAt: Instant? = null,

    /**
     * provider별로 "동의 필요 여부", "has_xxx" 같은 상태값/추가값을 최대한 담기
     * (DB에는 json으로 저장하거나 필요한 것만 골라 저장)
     */
    val extra: Map<String, Any?> = emptyMap(),

    /**
     * 원본 attrs를 통째로 보관하고 싶다면 여기
     * (주의: 그대로 DB에 박으면 용량/PII/스키마 이슈 -> 보통은 json 컬럼에 저장하거나 로깅 전용)
     */
    val raw: Map<String, Any?> = emptyMap(),
)

@Service
class OAuth2AccountService(
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
    private val userStatusPolicy: UserStatusPolicy,
) {

    @Transactional
    fun upsertAndGetUserId(info: OAuth2UserInfo): Long {
        val existing = userRepository.findByOAuthIncludingDeleted(info.provider, info.providerUserId)

        if (existing != null) {
            userStatusPolicy.validateLoginAllowed(existing)
            val updated = existing.applyOAuth2(info, objectMapper)
            userRepository.save(updated)
            return existing.id
        }

        val emailToSave = info.email?.takeIf { it.isNotBlank() }
            ?: "${info.provider.name.lowercase()}_${info.providerUserId}@oauth.local"

        val user = User(
            email = emailToSave,
            emailVerified = info.emailVerified ?: false,
            phoneNumber = info.phoneNumber,
            phoneNumberE164 = info.phoneNumberE164,
            phoneVerified = false,
            nickname = info.nickname ?: info.name,
            name = info.name ?: info.nickname,
            locale = info.locale ?: "ko-KR",
            gender = info.gender ?: Gender.UNKNOWN,
            birthday = info.birthday,
            birthyear = info.birthyear,
            ageRange = info.ageRange,
            profileImageUrl = info.profileImageUrl,
            thumbnailImageUrl = info.thumbnailImageUrl,
            authProvider = info.provider,
            oauthProviderUserId = info.providerUserId,
            oauthConnectedAt = info.connectedAt,
            oauthExtraJson = info.extra.takeIf { it.isNotEmpty() }?.let { objectMapper.writeValueAsString(it) },
            oauthRawJson = info.raw.takeIf { it.isNotEmpty() }?.let { objectMapper.writeValueAsString(it) },
        )

        return userRepository.save(user).id
    }

    private fun User.applyOAuth2(info: OAuth2UserInfo, om: ObjectMapper): User {
        return copy(
            email = info.email?.takeIf { it.isNotBlank() } ?: email,
            emailVerified = info.emailVerified ?: emailVerified,
            name = info.name?.takeIf { it.isNotBlank() } ?: name,
            nickname = info.nickname?.takeIf { it.isNotBlank() } ?: nickname,
            locale = info.locale?.takeIf { it.isNotBlank() } ?: locale,
            profileImageUrl = info.profileImageUrl?.takeIf { it.isNotBlank() } ?: profileImageUrl,
            thumbnailImageUrl = info.thumbnailImageUrl?.takeIf { it.isNotBlank() } ?: thumbnailImageUrl,
            gender = info.gender ?: gender,
            birthday = info.birthday?.takeIf { it.isNotBlank() } ?: birthday,
            birthyear = info.birthyear?.takeIf { it.isNotBlank() } ?: birthyear,
            ageRange = info.ageRange?.takeIf { it.isNotBlank() } ?: ageRange,
            phoneNumber = info.phoneNumber?.takeIf { it.isNotBlank() } ?: phoneNumber,
            phoneNumberE164 = info.phoneNumberE164?.takeIf { it.isNotBlank() } ?: phoneNumberE164,
            oauthConnectedAt = info.connectedAt ?: oauthConnectedAt,
            oauthExtraJson = if (info.extra.isNotEmpty()) om.writeValueAsString(info.extra) else oauthExtraJson,
            oauthRawJson = if (info.raw.isNotEmpty()) om.writeValueAsString(info.raw) else oauthRawJson,
        )
    }
}
