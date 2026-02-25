package io.soo.springboot.storage.db.core

import java.time.LocalDateTime

interface LocalCredentialRepository {
    fun save(credential: LocalCredential): LocalCredential
    fun findByUserId(userId: Long): LocalCredential?
    fun findByUserEmail(email: String): LocalCredential?
    fun lockByUserId(userId: Long): LocalCredential?
    fun deleteByUserId(userId: Long): Int
}