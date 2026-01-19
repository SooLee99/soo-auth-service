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
        val sid = normSessionId(sessionId)
        val did = normDeviceId(deviceId)

        val entity = userSessionMapRepository.lockBySessionId(sid)
            ?: UserSessionMapEntity(
                sessionId = sid,
                userId = userId,
                deviceId = did,
                provider = provider,
                lastAccessedAt = now,
                revokedAt = null,
                revokedReason = null,
            )

        // 이미 있던 세션이면 바인딩 정보 갱신 (재로그인/재발급 대응)
        entity.userId = userId
        entity.deviceId = did
        entity.provider = provider
        entity.lastAccessedAt = now
        entity.revokedAt = null
        entity.revokedReason = null

        userSessionMapRepository.save(entity)
    }

    fun findActive(sessionId: String): UserSessionMapEntity? =
        userSessionMapRepository.findBySessionIdAndRevokedAtIsNull(normSessionId(sessionId))

    @Transactional
    fun touch(sessionId: String) {
        val sid = normSessionId(sessionId)
        val m = userSessionMapRepository.lockBySessionId(sid) ?: return
        if (m.revokedAt != null) return

        m.lastAccessedAt = LocalDateTime.now()
        userSessionMapRepository.save(m)
    }

    /**
     * ✅ 세션 revoke(멱등)
     */
    @Transactional
    fun revoke(sessionId: String, reason: String?) {
        val m = userSessionMapRepository.findBySessionId(sessionId) ?: return

        if (m.revokedAt != null) return
        m.revokedAt = LocalDateTime.now()
        m.revokedReason = reason
        userSessionMapRepository.save(m)
    }
}
