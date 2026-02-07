package io.soo.springboot.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface LocalCredentialRepository : JpaRepository<LocalCredentialEntity, Long> {
    fun findByUserEmail(userEmail: String): LocalCredentialEntity?
    fun existsByUserEmail(userEmail: String): Boolean
}
