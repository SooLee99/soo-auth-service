package io.soo.springboot.core.domain

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.storage.db.core.UserSessionMapEntity
import io.soo.springboot.storage.db.core.UserSessionMapRepository

@Service
class UserSessionMapService(
    private val userSessionMapRepository: UserSessionMapRepository,
) {
    companion object {
        private const val MAX_SESSION_ID = 100
        private const val MAX_DEVICE_ID = 255
    }

    private fun normSessionId(sessionId: String) = sessionId.trim().take(MAX_SESSION_ID)
    private fun normDeviceId(deviceId: String) = deviceId.trim().take(MAX_DEVICE_ID)

    /**
     * session_id 유니크를 전제로 "upsert bind"로 동작 (중복 저장/경쟁 조건 방지)
     */
    @Transactional
    fun bind(sessionId: String, userId: Long, deviceId: String, provider: AuthProvider) {
        val now = LocalDateTime.now()

        val existing = userSessionMapRepository.findBySessionId(sessionId)
        if (existing != null) {
            // 같은 sessionId가 이미 있으면 갱신(재로그인/재바인딩 케이스 방어)
            existing.userId = userId
            existing.deviceId = deviceId
            existing.provider = provider
            existing.lastAccessedAt = now
            existing.revokedAt = null
            existing.revokedReason = null
            userSessionMapRepository.save(existing)
            return
        }

        userSessionMapRepository.save(
            UserSessionMapEntity(
                sessionId = sessionId,
                userId = userId,
                deviceId = deviceId,
                provider = provider,
                lastAccessedAt = now,
                revokedAt = null,
                revokedReason = null,
            )
        )
    }

    fun findActive(sessionId: String): UserSessionMapEntity? =
        userSessionMapRepository.findBySessionIdAndRevokedAtIsNull(normSessionId(sessionId))

    @Transactional
    fun touch(sessionId: String) {
        val m = userSessionMapRepository.findBySessionId(sessionId) ?: return
        if (m.revokedAt != null) return
        m.lastAccessedAt = LocalDateTime.now()
        userSessionMapRepository.save(m)
    }

    @Transactional
    fun revokeSession(sessionId: String, reason: String?) {
        val m = userSessionMapRepository.findBySessionId(sessionId) ?: return
        if (m.revokedAt != null) return
        m.revokedAt = LocalDateTime.now()
        m.revokedReason = reason
        userSessionMapRepository.save(m)
    }

    @Transactional(readOnly = true)
    fun activeSessionIds(userId: Long, deviceId: String? = null): List<String> =
        if (deviceId.isNullOrBlank())
            userSessionMapRepository.findActiveSessionIdsByUserId(userId)
        else
            userSessionMapRepository.findActiveSessionIdsByUserIdAndDeviceId(userId, deviceId)

}
