package io.soo.springboot.core.domain

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

// TODO: 의존성 역전 발생. 추후 대처 필요.
import io.soo.springboot.core.api.controller.v1.response.AdminLoginAttemptDto
import io.soo.springboot.core.api.controller.v1.response.AdminLoginAttemptStatsDto
import io.soo.springboot.core.api.controller.v1.response.KeyCountDto
import io.soo.springboot.core.api.controller.v1.response.PagedResult
import io.soo.springboot.core.api.controller.v1.response.toPageMetaDto

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.storage.db.core.LoginAttemptRepository

@Service
class AdminLoginAttemptQueryService(
    private val loginAttemptRepository: LoginAttemptRepository,
) {

    @Transactional(readOnly = true)
    fun searchLoginAttempts(
        page: Int,
        size: Int,
        success: Boolean?,
        provider: AuthProvider?,
        userId: Long?,
        deviceId: String?,
        ip: String?,
        errorCode: String?,
        from: LocalDateTime?,
        to: LocalDateTime?,
    ): PagedResult<AdminLoginAttemptDto> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val spec = LoginAttemptSpecs.filter(success, provider, userId, deviceId, ip, errorCode, from, to)

        val p = loginAttemptRepository.findAll(spec, pageable)
        val items = p.content.map {
            // TODO: DTO에서 메서드로 묶기
            AdminLoginAttemptDto(
                id = it.id,
                success = it.success,
                provider = it.provider,
                userId = it.userId,
                providerUserId = it.providerUserId,
                deviceId = it.deviceId,
                ip = it.ip,
                userAgent = it.userAgent,
                errorCode = it.errorCode,
                errorMessage = it.errorMessage,
                createdAt = it.createdAt,
            )
        }

        return PagedResult(items = items, page = p.toPageMetaDto())
    }

    /**
     * ✅ 로그인 시도 단건 상세 조회
     * - 운영 UI에서 목록 클릭 → 상세 팝업/페이지에 사용
     */
    @Transactional(readOnly = true)
    fun getLoginAttempt(attemptId: Long): AdminLoginAttemptDto {
        val a = loginAttemptRepository.findById(attemptId)
            .orElseThrow { NoSuchElementException("LoginAttempt not found: $attemptId") }
        // TODO: DTO에서 메서드로 묶기
        return AdminLoginAttemptDto(
            id = a.id,
            success = a.success,
            provider = a.provider,
            userId = a.userId,
            providerUserId = a.providerUserId,
            deviceId = a.deviceId,
            ip = a.ip,
            userAgent = a.userAgent,
            errorCode = a.errorCode,
            errorMessage = a.errorMessage,
            createdAt = a.createdAt,
        )
    }

    /**
     * ✅ 로그인 시도 통계
     * - from/to를 안 주면 기본 "최근 7일"로 계산(너무 큰 범위 조회 방지)
     */
    @Transactional(readOnly = true)
    fun getStats(
        provider: AuthProvider?,
        userId: Long?,
        deviceId: String?,
        ip: String?,
        from: LocalDateTime?,
        to: LocalDateTime?,
        topLimit: Int = 20,
    ): AdminLoginAttemptStatsDto {
        val now = LocalDateTime.now()

        val effectiveTo = to ?: now
        val effectiveFrom = from ?: effectiveTo.minusDays(7) // ✅ 기본 7일

        val total = loginAttemptRepository.countAllInRange(effectiveFrom, effectiveTo, provider, userId, deviceId, ip)
        val successCount = loginAttemptRepository.countSuccessInRange(effectiveFrom, effectiveTo, provider, userId, deviceId, ip)
        val failureCount = loginAttemptRepository.countFailureInRange(effectiveFrom, effectiveTo, provider, userId, deviceId, ip)

        val byProvider = loginAttemptRepository.groupByProviderInRange(effectiveFrom, effectiveTo, provider, userId, deviceId, ip)
            .map { row ->
                // TODO: DTO에서 메서드로 묶기
                KeyCountDto(
                    key = row.key?.toString() ?: "UNKNOWN",
                    count = row.cnt,
                )
            }

        val failureByErrorCode = loginAttemptRepository.failureGroupByErrorCodeInRange(effectiveFrom, effectiveTo, provider, userId, deviceId, ip)
            .map { row ->
                // TODO: DTO에서 메서드로 묶기
                KeyCountDto(
                    key = row.key?.toString() ?: "NONE",
                    count = row.cnt,
                )
            }

        val topPageable = PageRequest.of(0, topLimit)

        val topFailedIps = loginAttemptRepository.topFailedIpsInRange(effectiveFrom, effectiveTo, provider, userId, deviceId, ip, topPageable)
            .map { row ->
                // TODO: DTO에서 메서드로 묶기
                KeyCountDto(
                    key = row.key?.toString() ?: "UNKNOWN",
                    count = row.cnt,
                )
            }

        val topFailedDeviceIds = loginAttemptRepository.topFailedDeviceIdsInRange(effectiveFrom, effectiveTo, provider, userId, deviceId, ip, topPageable)
            .map { row ->
                // TODO: DTO에서 메서드로 묶기
                KeyCountDto(
                    key = row.key?.toString() ?: "UNKNOWN",
                    count = row.cnt,
                )
            }

        // TODO: DTO에서 메서드로 묶기
        return AdminLoginAttemptStatsDto(
            from = effectiveFrom,
            to = effectiveTo,
            total = total,
            success = successCount,
            failure = failureCount,
            byProvider = byProvider,
            failureByErrorCode = failureByErrorCode,
            topFailedIps = topFailedIps,
            topFailedDeviceIds = topFailedDeviceIds,
        )
    }

    /**
     * ✅ 특정 유저의 최근 로그인 시도 N건 조회
     * - 유저 상세 화면에서 “최근 로그인 시도” 탭/섹션에 사용하기 좋음
     */
    @Transactional(readOnly = true)
    fun recentByUser(userId: Long, size: Int): List<AdminLoginAttemptDto> {
        val safeSize = size.coerceIn(1, 200) // 과도한 조회 방지(원하는 정책으로 조정)
        val pageable = PageRequest.of(0, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))

        val list = loginAttemptRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)

        return list.map {
            // TODO: DTO에서 메서드로 묶기
            AdminLoginAttemptDto(
                id = it.id,
                success = it.success,
                provider = it.provider,
                userId = it.userId,
                providerUserId = it.providerUserId,
                deviceId = it.deviceId,
                ip = it.ip,
                userAgent = it.userAgent,
                errorCode = it.errorCode,
                errorMessage = it.errorMessage,
                createdAt = it.createdAt,
            )
        }
    }
}
