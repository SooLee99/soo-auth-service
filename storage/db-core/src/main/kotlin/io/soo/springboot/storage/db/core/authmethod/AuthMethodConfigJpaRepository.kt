package io.soo.springboot.storage.db.core.authmethod

import io.soo.springboot.core.enums.AuthMethod
import org.springframework.data.jpa.repository.JpaRepository

interface AuthMethodConfigJpaRepository : JpaRepository<AuthMethodConfigEntity, Long> {
    fun findByMethod(method: AuthMethod): AuthMethodConfigEntity?
}
