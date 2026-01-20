package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface OAuthIdentityRepository : JpaRepository<OAuthIdentityEntity, Long> {

    fun findAllByUserId(userId: Long): List<OAuthIdentityEntity>
    fun findAllByUserIdIn(userIds: Collection<Long>): List<OAuthIdentityEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select o from OAuthIdentityEntity o " +
            "where o.provider = :provider and o.providerUserId = :providerUserId",
    )
    fun lockByProviderAndProviderUserId(
        @Param("provider") provider: AuthProvider,
        @Param("providerUserId") providerUserId: String,
    ): OAuthIdentityEntity?

    @Modifying
    @Query(
        """
        update OAuthIdentityEntity o
           set o.deletedAt = :now,
               o.deletedReason = :reason,
               o.entityStatus = io.soo.springboot.core.enums.EntityStatus.DELETED
         where o.userId = :userId
           and o.entityStatus <> io.soo.springboot.core.enums.EntityStatus.DELETED
        """
    )
    fun softDeleteByUserId(
        @Param("userId") userId: Long,
        @Param("now") now: LocalDateTime,
        @Param("reason") reason: String,
    ): Int

    @Modifying
    @Query("delete from OAuthIdentityEntity o where o.userId = :userId")
    fun deleteAllByUserId(@Param("userId") userId: Long): Int
}
