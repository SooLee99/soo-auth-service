package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider
import org.springframework.stereotype.Repository

@Repository
class UserRepositoryImpl(
    private val jpaRepository: UserJpaRepository
) : UserRepository {

    override fun save(user: User): User {
        val entity = user.toEntity()
        val saved = jpaRepository.save(entity)
        return saved.toModel()
    }

    override fun findById(id: Long): User? {
        return jpaRepository.findById(id)
            .map { it.toModel() }
            .orElse(null)
    }

    override fun findByEmail(email: String): User? {
        return jpaRepository.findByEmail(email)?.toModel()
    }

    override fun findByOAuth(provider: AuthProvider, providerId: String): User? {
        return jpaRepository.findByAuthProviderAndOauthProviderUserId(provider, providerId)
            ?.toModel()
    }

    override fun existsByEmail(email: String): Boolean {
        return jpaRepository.existsByEmail(email)
    }

    override fun existsByPhoneNumber(phoneNumber: String): Boolean {
        return jpaRepository.existsByPhoneNumber(phoneNumber)
    }

    // ===== Mapper (내부에서만 사용) =====

    private fun User.toEntity(): UserEntity {
        return UserEntity(
            email = email,
            emailVerified = emailVerified,
            phoneNumber = phoneNumber,
            phoneVerified = phoneVerified,
            name = name,
            nickname = nickname,
            gender = gender,
            locale = locale,
            birthyear = birthyear,
            birthday = birthday,
            profileImageUrl = profileImageUrl,
            thumbnailImageUrl = thumbnailImageUrl,
            authProvider = authProvider,
            oauthProviderUserId = oauthProviderUserId,
            role = role,
        ).also {
            if (id > 0) it.id = id
        }
    }

    private fun UserEntity.toModel(): User {
        return User(
            id = id,
            email = email ?: "",
            emailVerified = emailVerified,
            phoneNumber = phoneNumber,
            phoneVerified = phoneVerified,
            name = name,
            nickname = nickname,
            gender = gender,
            locale = locale,
            birthyear = birthyear,
            birthday = birthday,
            profileImageUrl = profileImageUrl,
            thumbnailImageUrl = thumbnailImageUrl,
            authProvider = authProvider,
            oauthProviderUserId = oauthProviderUserId,
            role = role,
        )
    }
}