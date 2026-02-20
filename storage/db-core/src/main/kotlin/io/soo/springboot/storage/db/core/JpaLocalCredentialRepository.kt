package io.soo.springboot.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface JpaLocalCredentialRepository : JpaRepository<LocalCredentialEntity, Long> {
    fun findByUserId(userId: Long): LocalCredentialEntity?
    fun findByUserEmail(userEmail: String): LocalCredentialEntity?
}
