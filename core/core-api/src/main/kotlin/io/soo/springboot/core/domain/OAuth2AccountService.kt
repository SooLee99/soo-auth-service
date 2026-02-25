package io.soo.springboot.core.domain

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.storage.db.core.User
import io.soo.springboot.storage.db.core.UserRepository
import io.soo.springboot.core.enums.Gender
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OAuth2AccountService(
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    fun upsertAndGetUserId(info: OAuth2UserInfo): Long {
        val existing = userRepository.findByOAuth(info.provider, info.providerUserId)

        if (existing != null) {
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

    // ✅ data class는 immutable이므로 copy 사용
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