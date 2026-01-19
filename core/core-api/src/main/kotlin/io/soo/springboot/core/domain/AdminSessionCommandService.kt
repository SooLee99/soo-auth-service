package io.soo.springboot.core.domain

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

import io.soo.springboot.storage.db.core.UserSessionMapRepository

@Service
class AdminSessionCommandService(
    private val userSessionMapRepository: UserSessionMapRepository,
) {

    @Transactional
    fun revokeSession(sessionId: String, reason: String?) {
        val m = userSessionMapRepository.findBySessionId(sessionId)
            ?: throw NoSuchElementException("Session not found: $sessionId")

        // ✅ 이미 revoke 된 세션이면 멱등 처리
        if (m.revokedAt != null) return

        m.revokedAt = LocalDateTime.now()
        m.revokedReason = reason
        userSessionMapRepository.save(m)
    }

    @Transactional
    fun revokeAllSessions(userId: Long, deviceId: String?, reason: String?) {
        val list = if (deviceId.isNullOrBlank()) {
            userSessionMapRepository.findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(userId)
        } else {
            userSessionMapRepository.findAllByUserIdAndDeviceIdAndRevokedAtIsNull(userId, deviceId)
        }

        val now = LocalDateTime.now()
        list.forEach {
            it.revokedAt = now
            it.revokedReason = reason
        }
        userSessionMapRepository.saveAll(list)
    }
}
