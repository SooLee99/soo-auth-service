package io.soo.springboot.core.domain

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

// TODO: 의존성 역전 발생. 추후 대처 필요.
import io.soo.springboot.core.api.controller.v1.response.AdminLoginAttemptDto
import io.soo.springboot.core.api.controller.v1.response.AdminOAuthIdentityResponse
import io.soo.springboot.core.api.controller.v1.response.AdminSessionResponse
import io.soo.springboot.core.api.controller.v1.response.AdminUserDetailResponse
import io.soo.springboot.core.api.controller.v1.response.AdminUserDevicResponse
import io.soo.springboot.core.api.controller.v1.response.AdminUserSummaryResponse
import io.soo.springboot.core.api.controller.v1.response.PagedResult
import io.soo.springboot.core.api.controller.v1.response.toPageMetaDto

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.storage.db.core.*

@Service
class AdminUserQueryService(
    private val userAccountRepository: UserAccountRepository,
    private val oauthIdentityRepository: OAuthIdentityRepository,
    private val userDeviceRepository: UserDeviceRepository,
    private val userSessionMapRepository: UserSessionMapRepository,
    private val loginAttemptRepository: LoginAttemptRepository,
) {

    /**
     * ✅ 유저 목록 조회 (N+1 제거 버전)
     * - user page 1번
     * - identities 1번 (IN)
     * - device count 1번 (group by)
     * - active session count 1번 (group by)
     */
    @Transactional(readOnly = true)
    fun listUsers(
        page: Int,
        size: Int,
        q: String?,
        email: String?,
        nickname: String?,
        provider: AuthProvider?,
        suspended: Boolean?,
        createdFrom: LocalDateTime?,
        createdTo: LocalDateTime?,
    ): PagedResult<AdminUserSummaryResponse> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val spec = UserAccountSpecs.filter(q, email, nickname, provider, suspended, createdFrom, createdTo)

        val p = userAccountRepository.findAll(spec, pageable)
        val users = p.content
        if (users.isEmpty()) return PagedResult(items = emptyList(), page = p.toPageMetaDto())

        val userIds = users.map { it.id }

        // ✅ providers 배치 조회
        val identities = oauthIdentityRepository.findAllByUserIdIn(userIds)
        val providersByUserId = identities
            .groupBy { it.userId }
            .mapValues { (_, list) -> list.map { it.provider }.distinct() }

        // ✅ device count 집계
        val deviceCountByUserId = userDeviceRepository.countByUserIdIn(userIds)
            .associate { it.userId to it.cnt.toInt() }

        // ✅ active session count 집계
        val activeSessionCountByUserId = userSessionMapRepository.countActiveByUserIdIn(userIds)
            .associate { it.userId to it.cnt.toInt() }

        val items = users.map { u ->
            // TODO: DTO에서 메서드로 묶기
            AdminUserSummaryResponse(
                userId = u.id,
                email = u.email,
                nickname = u.nickname,
                profileImageUrl = u.profileImageUrl,
                thumbnailImageUrl = u.thumbnailImageUrl,
                createdAt = u.createdAt,
                updatedAt = u.updatedAt,
                providers = providersByUserId[u.id].orEmpty(),
                deviceCount = deviceCountByUserId[u.id] ?: 0,
                activeSessionCount = activeSessionCountByUserId[u.id] ?: 0,
            )
        }

        return PagedResult(items = items, page = p.toPageMetaDto())
    }

    /**
     * ✅ 유저 상세(연동/디바이스/세션/최근 로그인 시도 포함)
     */
    @Transactional(readOnly = true)
    fun getUserDetail(userId: Long): AdminUserDetailResponse {
        val user = userAccountRepository.findById(userId)
            .orElseThrow { NoSuchElementException("User not found: $userId") }

        // TODO: DTO에서 메서드로 묶기
        val identities = oauthIdentityRepository.findAllByUserId(userId).map {
            AdminOAuthIdentityResponse(
                id = it.id,
                provider = it.provider,
                providerUserId = it.providerUserId,
                createdAt = it.createdAt,
            )
        }

        val devices = userDeviceRepository.findAllByUserId(userId).map {
            AdminUserDevicResponse(
                id = it.id,
                userId = it.userId,
                deviceId = it.deviceId,
                status = it.deviceStatus,
                blockedReason = it.blockedReason,
                blockedAt = it.blockedAt,
                lastLoginAt = it.lastLoginAt,
                lastIp = it.lastIp,
                lastUserAgent = it.lastUserAgent,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
            )
        }

        val sessions = userSessionMapRepository.findAllByUserIdOrderByCreatedAtDesc(userId).map {
            AdminSessionResponse(
                id = it.id,
                sessionId = it.sessionId,
                userId = it.userId,
                deviceId = it.deviceId,
                provider = it.provider,
                createdAt = it.createdAt,
                lastAccessedAt = it.lastAccessedAt,
                revokedAt = it.revokedAt,
                revokedReason = it.revokedReason,
            )
        }

        val attempts = loginAttemptRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId).map {
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

        return AdminUserDetailResponse(
            userId = user.id,
            email = user.email,
            nickname = user.nickname,
            profileImageUrl = user.profileImageUrl,
            thumbnailImageUrl = user.thumbnailImageUrl,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt,
            identities = identities,
            devices = devices,
            sessions = sessions,
            lastLoginAttempts = attempts,
        )
    }
}
