package io.soo.springboot.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface UserAccountRepository : JpaRepository<UserAccountEntity, Long>, JpaSpecificationExecutor<UserAccountEntity> {
    fun findByEmail(email: String): UserAccountEntity?

    @Query(
        """
        select u.id
          from UserAccountEntity u
         where u.entityStatus = io.soo.springboot.core.enums.EntityStatus.DELETED
           and u.deletedAt is not null
           and u.deletedAt < :cutoff
        """
    )
    fun findUserIdsToPurge(@Param("cutoff") cutoff: LocalDateTime): List<Long>
}
