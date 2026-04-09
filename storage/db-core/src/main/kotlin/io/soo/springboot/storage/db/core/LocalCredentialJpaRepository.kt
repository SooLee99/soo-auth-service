package io.soo.springboot.storage.db.core

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface LocalCredentialJpaRepository : JpaRepository<LocalCredentialEntity, Long> {
    fun findByUserId(userId: Long): LocalCredentialEntity?
    fun findByUserEmail(email: String): LocalCredentialEntity?

    // 로그인 실패 카운트/잠금 업데이트 시 동시성 방지
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LocalCredentialEntity c where c.userId = :userId")
    fun lockByUserId(@Param("userId") userId: Long): LocalCredentialEntity?

    @Modifying
    @Query("delete from LocalCredentialEntity c where c.userId = :userId")
    fun deleteByUserId(@Param("userId") userId: Long): Int
}
