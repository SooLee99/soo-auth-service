package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.DeviceStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface UserDeviceRepository : JpaRepository<UserDeviceEntity, Long> {

    fun findByUserIdAndDeviceId(userId: Long, deviceId: String): UserDeviceEntity?
    fun findAllByUserId(userId: Long): List<UserDeviceEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from UserDeviceEntity d where d.userId = :userId and d.deviceId = :deviceId")
    fun lockByUserIdAndDeviceId(
        @Param("userId") userId: Long,
        @Param("deviceId") deviceId: String,
    ): UserDeviceEntity?

    /**
     * ✅ userId별 디바이스 개수 집계 (group by)
     */
    @Query(
        """
        select d.userId as userId, count(d) as cnt
        from UserDeviceEntity d
        where d.userId in :userIds
        group by d.userId
        """,
    )
    fun countByUserIdIn(@Param("userIds") userIds: Collection<Long>): List<UserIdCountRow>

    @Modifying
    @Query(
        """
        update UserDeviceEntity d
            set d.entityStatus = : status,
                d.blockedReason = : reason,
                d.blockedAt = : now
        where d.userId in :userId
        """
    )
    fun markWithdrawnByUserId(
        @Param("userId") userId:Long,
        @Param("now") now: LocalDateTime,
        @Param("reason") reason: String,
        @Param("status") status: DeviceStatus = DeviceStatus.INACTIVE,
    ): Int

    @Modifying
    @Query("delete from UserDeviceEntity d where d.userId = :userId")
    fun deleteAllByUserId(@Param("userId") userId: Long): Int

}

interface UserIdCountRow {
    val userId: Long
    val cnt: Long
}
