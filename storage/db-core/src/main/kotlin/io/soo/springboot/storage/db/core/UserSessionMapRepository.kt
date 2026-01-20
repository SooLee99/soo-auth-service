package io.soo.springboot.storage.db.core

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserSessionMapRepository : JpaRepository<UserSessionMapEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from UserSessionMapEntity m where m.sessionId = :sessionId")
    fun lockBySessionId(@Param("sessionId") sessionId: String): UserSessionMapEntity?
    fun findBySessionIdAndRevokedAtIsNull(sessionId: String): UserSessionMapEntity?
    fun findBySessionId(sessionId: String): UserSessionMapEntity?

    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long): List<UserSessionMapEntity>

    @Query(
        """
        select m.sessionId
        from UserSessionMapEntity m
        where m.userId = :userId and m.revokedAt is null
        """,
    )
    fun findAllByUserIdAndDeviceIdAndRevokedAtIsNull(userId: Long, deviceId: String): List<UserSessionMapEntity>
    fun findAllByUserIdAndDeviceIdOrderByCreatedAtDesc(userId: Long, deviceId: String): List<UserSessionMapEntity>
    fun findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(userId: Long): List<UserSessionMapEntity>

    /**
     * ✅ userId별 활성 세션 개수 집계
     * - revokedAt이 null인 세션만 “활성”
     */
    @Query(
        """
        select s.userId as userId, count(s) as cnt
        from UserSessionMapEntity s
        where s.userId in :userIds
          and s.revokedAt is null
        group by s.userId
        """,
    )
    fun countActiveByUserIdIn(@Param("userIds") userIds: Collection<Long>): List<UserIdCountRow>

    @Query("select m.sessionId from UserSessionMapEntity m where m.userId = :userId and m.revokedAt is null")
    fun findActiveSessionIdsByUserId(@Param("userId") userId: Long): List<String>

    @Query("select m.sessionId from UserSessionMapEntity m where m.userId = :userId and m.deviceId = :deviceId and m.revokedAt is null")
    fun findActiveSessionIdsByUserIdAndDeviceId(@Param("userId") userId: Long, @Param("deviceId") deviceId: String): List<String>

    @Modifying
    @Query("delete from UserSessionMapEntity m where m.userId = :userId")
    fun deleteAllByUserId(@Param("userId") userId: Long): Int
}
