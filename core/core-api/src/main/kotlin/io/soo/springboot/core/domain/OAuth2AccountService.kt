package io.soo.springboot.core.domain

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.core.enums.Gender
import io.soo.springboot.storage.db.core.UserEntity
import io.soo.springboot.storage.db.core.UserJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OAuth2AccountService(
    private val userJpaRepository: UserJpaRepository,
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    fun upsertAndGetUserId(info: OAuth2UserInfo): Long {
        val existing = userJpaRepository
            .findByAuthProviderAndOauthProviderUserId(info.provider, info.providerUserId)

        if (existing != null) {
            existing.applyOAuth2(info, objectMapper)
            return existing.id
        }

        val emailToSave =
            info.email?.takeIf { it.isNotBlank() }
                ?: "${info.provider.name.lowercase()}_${info.providerUserId}@oauth.local"

        val user = UserEntity(
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

        return userJpaRepository.save(user).id
    }

    private fun UserEntity.applyOAuth2(info: OAuth2UserInfo, om: ObjectMapper) {
        // email
        info.email?.takeIf { it.isNotBlank() }?.let { this.email = it }
        info.emailVerified?.let { this.emailVerified = it }

        // name/nickname (null 최소화)
        val newName = info.name?.takeIf { it.isNotBlank() }
        val newNick = info.nickname?.takeIf { it.isNotBlank() }

        if (newName != null) this.name = newName
        if (newNick != null) this.nickname = newNick

        // fallback: name 없고 nickname만 있으면 name 채우기
        if (this.name.isNullOrBlank() && !this.nickname.isNullOrBlank()) {
            this.name = this.nickname
        }
        if (this.nickname.isNullOrBlank() && !this.name.isNullOrBlank()) {
            this.nickname = this.name
        }

        // locale
        info.locale?.takeIf { it.isNotBlank() }?.let { this.locale = it }

        // profile
        info.profileImageUrl?.takeIf { it.isNotBlank() }?.let { this.profileImageUrl = it }
        info.thumbnailImageUrl?.takeIf { it.isNotBlank() }?.let { this.thumbnailImageUrl = it }

        // optional demographics
        info.gender?.let { this.gender = it }
        info.birthday?.takeIf { it.isNotBlank() }?.let { this.birthday = it }
        info.birthyear?.takeIf { it.isNotBlank() }?.let { this.birthyear = it }
        info.ageRange?.takeIf { it.isNotBlank() }?.let { this.ageRange = it }

        // phone
        info.phoneNumber?.takeIf { it.isNotBlank() }?.let { this.phoneNumber = it }
        info.phoneNumberE164?.takeIf { it.isNotBlank() }?.let { this.phoneNumberE164 = it }

        // provider meta
        info.connectedAt?.let { this.oauthConnectedAt = it }

        if (info.extra.isNotEmpty()) this.oauthExtraJson = om.writeValueAsString(info.extra)
        if (info.raw.isNotEmpty()) this.oauthRawJson = om.writeValueAsString(info.raw)
    }
}