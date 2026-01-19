package io.soo.springboot.core.domain

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.UserAccountRepository

@Service
class LocalLoginPolicyService(
    private val userAccountRepository: UserAccountRepository,
    private val localCredentialRepository: LocalCredentialRepository,
) {
    @Transactional
    fun recordFailureByEmail(normalizedEmail: String, now: LocalDateTime = LocalDateTime.now()): FailureResult {
        val user = userAccountRepository.findByEmail(normalizedEmail) ?: return FailureResult.NOT_FOUND
        val cred = localCredentialRepository.lockByUserId(user.id) ?: return FailureResult.NOT_FOUND

        // ✅ 실패 기록(잠금 중이면 now+1분 갱신 포함)
        cred.recordLoginFailure(
            now = now,
            maxAttempts = 5,
            lockMinutes = 5,
            extendLockMinutesWhenLocked = 1,
        )
        localCredentialRepository.save(cred)

        return if (cred.isLocked(now)) FailureResult.LOCKED else FailureResult.BAD_CREDENTIALS
    }

    @Transactional
    fun recordSuccessByUserId(userId: Long, now: LocalDateTime = LocalDateTime.now()) {
        val cred = localCredentialRepository.lockByUserId(userId) ?: return
        cred.recordLoginSuccess(now)
        localCredentialRepository.save(cred)
    }

    enum class FailureResult { NOT_FOUND, BAD_CREDENTIALS, LOCKED }
}
