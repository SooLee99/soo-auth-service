package io.soo.springboot.core.domain

import io.soo.springboot.core.enums.Gender
import io.soo.springboot.storage.db.core.UserEntity
import io.soo.springboot.storage.db.core.UserJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OAuth2AccountService(
    private val userJpaRepository: UserJpaRepository,
) {

    @Transactional
    fun upsertAndGetUserId(info: OAuth2UserInfo): Long {
        val existing = userJpaRepository
            .findByAuthProviderAndOauthProviderUserId(info.provider, info.providerUserId)

        if (existing != null) {
            if (!info.email.isNullOrBlank()) existing.email = info.email
            if (!info.nickname.isNullOrBlank()) existing.nickname = info.nickname
            return existing.id
        }

        // "kakao_{id}@oauth.local" 같은 가짜 email 생성
        val emailToSave = info.email ?: "${info.provider.name.lowercase()}_${info.providerUserId}@oauth.local"

        val user = userJpaRepository.save(
            UserEntity(
                email = emailToSave,
                emailVerified = false,
                phoneNumber = null,
                phoneVerified = false,
                name = null,
                nickname = info.nickname,
                gender = Gender.UNKNOWN,
                locale = "ko-KR",
                birthyear = null,
                birthday = null,
                profileImageUrl = null,
                thumbnailImageUrl = null,
                authProvider = info.provider,
                oauthProviderUserId = info.providerUserId,
            )
        )
        return user.id
    }
}