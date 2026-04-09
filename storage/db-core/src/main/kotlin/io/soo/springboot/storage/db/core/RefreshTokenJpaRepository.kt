package io.soo.springboot.storage.db.core

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface RefreshTokenJpaRepository : JpaRepository<RefreshTokenEntity, Long> {

    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshTokenEntity r where r.tokenHash = :hash")
    fun findForUpdateByHash(@Param("hash") hash: String): RefreshTokenEntity?

    @Modifying
    @Query(
        """
        update RefreshTokenEntity r
        set r.revokedAt = :now
        where r.userId = :userId
          and r.revokedAt is null
          and r.expiresAt > :now
    """,
    )
    fun revokeAllActiveByUser(@Param("userId") userId: Long, @Param("now") now: Instant): Int

    @Modifying
    @Query("delete from RefreshTokenEntity r where r.expiresAt < :before")
    fun deleteExpired(@Param("before") before: Instant): Int

    @Query(
        """
        select r from RefreshTokenEntity r
        where r.userId = :userId
          and r.deviceId = :deviceId
          and r.revokedAt is null
          and r.expiresAt > :now
        """,
    )
    fun findActiveByUserIdAndDeviceId(
        @Param("userId") userId: Long,
        @Param("deviceId") deviceId: String,
        @Param("now") now: Instant,
    ): List<RefreshTokenEntity>

    @Query(
        """
        select r from RefreshTokenEntity r
        where r.userId = :userId
          and r.revokedAt is null
          and r.expiresAt > :now
        """,
    )
    fun findActiveByUserId(
        @Param("userId") userId: Long,
        @Param("now") now: Instant,
    ): List<RefreshTokenEntity>

    @Modifying
    @Query(
        """
        update RefreshTokenEntity r
           set r.revokedAt = :now
         where r.userId = :userId
           and r.revokedAt is null
        """,
    )
    fun revokeAllActiveByUserId(
        @Param("userId") userId: Long,
        @Param("now") now: Instant,
    ): Int

    @Modifying
    @Query(
        """
        update RefreshTokenEntity r
           set r.revokedAt = :now
         where r.userId = :userId
           and r.deviceId = :deviceId
           and r.revokedAt is null
        """,
    )
    fun revokeAllActiveByUserIdAndDeviceId(
        @Param("userId") userId: Long,
        @Param("deviceId") deviceId: String,
        @Param("now") now: Instant,
    ): Int
}
