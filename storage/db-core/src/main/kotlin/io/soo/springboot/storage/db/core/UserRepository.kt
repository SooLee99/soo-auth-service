package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Role
import io.soo.springboot.core.enums.UserStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.Instant

interface UserRepository {
    fun save(user: User): User
    fun findById(id: Long): User?
    fun findByIdIncludingDeleted(id: Long): User?
    fun findByEmail(email: String): User?
    fun findByEmailIncludingDeleted(email: String): User?
    fun findByOAuth(provider: AuthProvider, providerId: String): User?
    fun findByOAuthIncludingDeleted(provider: AuthProvider, providerId: String): User?
    fun existsByEmail(email: String): Boolean
    fun existsByPhoneNumber(phoneNumber: String): Boolean
    fun searchUsers(
        keyword: String?,
        userStatus: UserStatus?,
        role: Role?,
        authProvider: AuthProvider?,
        pageable: Pageable,
    ): Page<User>
    fun blocked(pageable: Pageable): Page<User>
    fun deleted(pageable: Pageable): Page<User>
    fun purgeSoftDeletedUsers(now: Instant): Int
}
