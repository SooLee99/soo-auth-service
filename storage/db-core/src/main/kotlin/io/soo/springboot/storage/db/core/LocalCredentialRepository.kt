package io.soo.springboot.storage.db.core

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface LocalCredentialRepository : JpaRepository<LocalCredentialEntity, Long> {
    fun findByUserId(userId: Long): LocalCredentialEntity?

    // ✅ 로그인 실패 카운트/잠금 업데이트 시 동시성 방지
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LocalCredentialEntity c where c.userId = :userId")
    fun lockByUserId(@Param("userId") userId: Long): LocalCredentialEntity?

    @Modifying
    @Query(
        """
        update LocalCredentialEntity c
           set c.deletedAt = :now,
               c.deletedReason = :reason,
               c.entityStatus = io.soo.springboot.core.enums.EntityStatus.DELETED
         where c.userId = :userId
           and c.entityStatus <> io.soo.springboot.core.enums.EntityStatus.DELETED
        """
    )
    fun softDeleteByUserId(
        @Param("userId") userId: Long,
        @Param("now") now: LocalDateTime,
        @Param("reason") reason: String,
    ): Int

    @Modifying
    @Query("delete from LocalCredentialEntity c where c.userId = :userId")
    fun deleteByUserId(@Param("userId") userId: Long): Int
}
