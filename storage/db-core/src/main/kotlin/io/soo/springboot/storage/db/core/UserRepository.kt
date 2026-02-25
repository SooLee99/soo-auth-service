package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider

interface UserRepository {
    fun save(user: User): User
    fun findById(id: Long): User?
    fun findByEmail(email: String): User?
    fun findByOAuth(provider: AuthProvider, providerId: String): User?
    fun existsByEmail(email: String): Boolean
    fun existsByPhoneNumber(phoneNumber: String): Boolean
}