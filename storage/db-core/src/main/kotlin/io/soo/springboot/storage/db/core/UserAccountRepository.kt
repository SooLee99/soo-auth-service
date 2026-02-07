package io.soo.springboot.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface UserAccountRepository : JpaRepository<UserAccountEntity, Long>, JpaSpecificationExecutor<UserAccountEntity> {
    fun findByEmail(email: String): UserAccountEntity?
    fun findByPhoneNumber(phoneNumber: String): UserAccountEntity?
}
