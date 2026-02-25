package io.soo.springboot.storage.db.core

interface LocalCredentialRepository {
    fun save(credential: LocalCredential): LocalCredential
    fun findByUserId(userId: Long): LocalCredential?
    fun findByUserEmail(email: String): LocalCredential?
}