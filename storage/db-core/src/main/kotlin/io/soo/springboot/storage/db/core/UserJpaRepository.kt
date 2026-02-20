package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface UserJpaRepository : JpaRepository<UserEntity, Long>, JpaSpecificationExecutor<UserEntity> {
    fun findByEmail(email: String): UserEntity?
    fun existsByEmail(email: String): Boolean
    fun existsByPhoneNumber(phoneNumber: String): Boolean
    fun findByAuthProviderAndOauthProviderUserId(
        authProvider: AuthProvider,
        oauthProviderUserId: String
    ): UserEntity?

    fun existsByAuthProviderAndOauthProviderUserId(
        authProvider: AuthProvider,
        oauthProviderUserId: String
    ): Boolean
}
