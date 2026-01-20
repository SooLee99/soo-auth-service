package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
interface LoginAttemptRepository :
    JpaRepository<LoginAttemptEntity, Long>,
    JpaSpecificationExecutor<LoginAttemptEntity> {

    fun findTop50ByUserIdOrderByCreatedAtDesc(userId: Long): List<LoginAttemptEntity>

    // ------------------------
    // ✅ 통계: 카운트 (전체/성공/실패)
    // ------------------------
    @Query(
        """
        select count(a)
        from LoginAttemptEntity a
        where a.createdAt between :from and :to
          and (:provider is null or a.provider = :provider)
          and (:userId is null or a.userId = :userId)
          and (:deviceId is null or a.deviceId = :deviceId)
          and (:ip is null or a.ip = :ip)
        """,
    )
    fun countAllInRange(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
        @Param("provider") provider: AuthProvider?,
        @Param("userId") userId: Long?,
        @Param("deviceId") deviceId: String?,
        @Param("ip") ip: String?,
    ): Long

    @Query(
        """
        select count(a)
        from LoginAttemptEntity a
        where a.createdAt between :from and :to
          and a.success = true
          and (:provider is null or a.provider = :provider)
          and (:userId is null or a.userId = :userId)
          and (:deviceId is null or a.deviceId = :deviceId)
          and (:ip is null or a.ip = :ip)
        """,
    )
    fun countSuccessInRange(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
        @Param("provider") provider: AuthProvider?,
        @Param("userId") userId: Long?,
        @Param("deviceId") deviceId: String?,
        @Param("ip") ip: String?,
    ): Long

    @Query(
        """
        select count(a)
        from LoginAttemptEntity a
        where a.createdAt between :from and :to
          and a.success = false
          and (:provider is null or a.provider = :provider)
          and (:userId is null or a.userId = :userId)
          and (:deviceId is null or a.deviceId = :deviceId)
          and (:ip is null or a.ip = :ip)
        """,
    )
    fun countFailureInRange(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
        @Param("provider") provider: AuthProvider?,
        @Param("userId") userId: Long?,
        @Param("deviceId") deviceId: String?,
        @Param("ip") ip: String?,
    ): Long

    // ------------------------
    // ✅ 통계: provider 분포
    // ------------------------
    @Query(
        """
        select a.provider as key, count(a) as cnt
        from LoginAttemptEntity a
        where a.createdAt between :from and :to
          and (:provider is null or a.provider = :provider)
          and (:userId is null or a.userId = :userId)
          and (:deviceId is null or a.deviceId = :deviceId)
          and (:ip is null or a.ip = :ip)
        group by a.provider
        order by count(a) desc
        """,
    )
    fun groupByProviderInRange(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
        @Param("provider") provider: AuthProvider?,
        @Param("userId") userId: Long?,
        @Param("deviceId") deviceId: String?,
        @Param("ip") ip: String?,
    ): List<KeyCountRow>

    // ------------------------
    // ✅ 통계: 실패 errorCode 분포 (null은 "NONE"으로 집계)
    // ------------------------
    @Query(
        """
        select coalesce(a.errorCode, 'NONE') as key, count(a) as cnt
        from LoginAttemptEntity a
        where a.createdAt between :from and :to
          and a.success = false
          and (:provider is null or a.provider = :provider)
          and (:userId is null or a.userId = :userId)
          and (:deviceId is null or a.deviceId = :deviceId)
          and (:ip is null or a.ip = :ip)
        group by coalesce(a.errorCode, 'NONE')
        order by count(a) desc
        """,
    )
    fun failureGroupByErrorCodeInRange(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
        @Param("provider") provider: AuthProvider?,
        @Param("userId") userId: Long?,
        @Param("deviceId") deviceId: String?,
        @Param("ip") ip: String?,
    ): List<KeyCountRow>

    // ------------------------
    // ✅ 통계: 실패 Top IP / DeviceId
    // - Pageable로 limit 처리
    // ------------------------
    @Query(
        """
        select a.ip as key, count(a) as cnt
        from LoginAttemptEntity a
        where a.createdAt between :from and :to
          and a.success = false
          and a.ip is not null
          and (:provider is null or a.provider = :provider)
          and (:userId is null or a.userId = :userId)
          and (:deviceId is null or a.deviceId = :deviceId)
          and (:ip is null or a.ip = :ip)
        group by a.ip
        order by count(a) desc
        """,
    )
    fun topFailedIpsInRange(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
        @Param("provider") provider: AuthProvider?,
        @Param("userId") userId: Long?,
        @Param("deviceId") deviceId: String?,
        @Param("ip") ip: String?,
        pageable: Pageable,
    ): List<KeyCountRow>

    @Query(
        """
        select a.deviceId as key, count(a) as cnt
        from LoginAttemptEntity a
        where a.createdAt between :from and :to
          and a.success = false
          and a.deviceId is not null
          and (:provider is null or a.provider = :provider)
          and (:userId is null or a.userId = :userId)
          and (:deviceId is null or a.deviceId = :deviceId)
          and (:ip is null or a.ip = :ip)
        group by a.deviceId
        order by count(a) desc
        """,
    )
    fun topFailedDeviceIdsInRange(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
        @Param("provider") provider: AuthProvider?,
        @Param("userId") userId: Long?,
        @Param("deviceId") deviceId: String?,
        @Param("ip") ip: String?,
        pageable: Pageable,
    ): List<KeyCountRow>

    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): List<LoginAttemptEntity>

    @Modifying
    @Query("delete from OAuthIdentityEntity o where o.userId = :userId")
    fun deleteAllByUserId(@Param("userId") userId: Long): Int
}

/**
 * ✅ 통계용 프로젝션(쿼리 결과를 간단히 받기)
 */
interface KeyCountRow {
    val key: Any?
    val cnt: Long
}
