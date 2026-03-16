package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.UserStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.Instant

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
            .filter { it.userStatus != UserStatus.SOFT_DELETED }
            .map { it.toModel() }
            .orElse(null)
    }

    override fun findByIdIncludingDeleted(id: Long): User? {
        return jpaRepository.findById(id)
            .map { it.toModel() }
            .orElse(null)
    }

    override fun findByEmail(email: String): User? {
        return jpaRepository.findByEmailAndUserStatusNot(email, UserStatus.SOFT_DELETED)?.toModel()
    }

    override fun findByEmailIncludingDeleted(email: String): User? {
        return jpaRepository.findByEmail(email)?.toModel()
    }

    override fun findByOAuth(provider: AuthProvider, providerId: String): User? {
        return jpaRepository.findByAuthProviderAndOauthProviderUserIdAndUserStatusNot(
            provider,
            providerId,
            UserStatus.SOFT_DELETED,
        )
            ?.toModel()
    }

    override fun findByOAuthIncludingDeleted(provider: AuthProvider, providerId: String): User? {
        return jpaRepository.findByAuthProviderAndOauthProviderUserId(provider, providerId)
            ?.toModel()
    }

    override fun existsByEmail(email: String): Boolean {
        return jpaRepository.existsByEmailAndUserStatusNot(email, UserStatus.SOFT_DELETED)
    }

    override fun existsByPhoneNumber(phoneNumber: String): Boolean {
        return jpaRepository.existsByPhoneNumberAndUserStatusNot(phoneNumber, UserStatus.SOFT_DELETED)
    }

    override fun findBlockedUsers(pageable: Pageable): Page<User> {
        return jpaRepository.findAllByUserStatus(UserStatus.BLOCKED, pageable).map { it.toModel() }
    }

    override fun findSoftDeletedUsers(pageable: Pageable): Page<User> {
        return jpaRepository.findAllByUserStatus(UserStatus.SOFT_DELETED, pageable).map { it.toModel() }
    }

    override fun purgeSoftDeletedUsers(now: Instant): Int {
        return jpaRepository.purgeSoftDeletedUsers(UserStatus.SOFT_DELETED, now)
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
            userStatus = userStatus,
            blocked = blocked,
            blockedReason = blockedReason,
            blockedAt = blockedAt,
            blockedByAdminId = blockedByAdminId,
            unblockedAt = unblockedAt,
            unblockedByAdminId = unblockedByAdminId,
            deletedAt = deletedAt,
            deletionReason = deletionReason,
            retentionUntil = retentionUntil,
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
            userStatus = userStatus,
            blocked = blocked,
            blockedReason = blockedReason,
            blockedAt = blockedAt,
            blockedByAdminId = blockedByAdminId,
            unblockedAt = unblockedAt,
            unblockedByAdminId = unblockedByAdminId,
            deletedAt = deletedAt,
            deletionReason = deletionReason,
            retentionUntil = retentionUntil,
        )
    }
}
