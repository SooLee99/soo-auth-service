package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.UserStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.query.Param
import java.time.Instant

interface UserJpaRepository : JpaRepository<UserEntity, Long>, JpaSpecificationExecutor<UserEntity> {
    fun findByEmailAndUserStatusNot(email: String, userStatus: UserStatus): UserEntity?
    fun findByEmail(email: String): UserEntity?
    fun findByPhoneNumberAndUserStatusNot(phoneNumber: String, userStatus: UserStatus): UserEntity?
    fun findByPhoneNumber(phoneNumber: String): UserEntity?
    fun existsByEmailAndUserStatusNot(email: String, userStatus: UserStatus): Boolean
    fun existsByEmail(email: String): Boolean
    fun existsByPhoneNumberAndUserStatusNot(phoneNumber: String, userStatus: UserStatus): Boolean
    fun existsByPhoneNumber(phoneNumber: String): Boolean
    fun findByAuthProviderAndOauthProviderUserId(
        authProvider: AuthProvider,
        oauthProviderUserId: String
    ): UserEntity?
    fun findByAuthProviderAndOauthProviderUserIdAndUserStatusNot(
        authProvider: AuthProvider,
        oauthProviderUserId: String,
        userStatus: UserStatus,
    ): UserEntity?

    fun findAllByBlockedTrue(pageable: Pageable): Page<UserEntity>
    fun findAllByUserStatus(userStatus: UserStatus, pageable: Pageable): Page<UserEntity>

    @Modifying
    @Query(
        """
        delete from UserEntity u
        where u.userStatus = :status
          and u.retentionUntil is not null
          and u.retentionUntil < :now
        """
    )
    fun purgeSoftDeletedUsers(@Param("status") status: UserStatus, @Param("now") now: Instant): Int
}
