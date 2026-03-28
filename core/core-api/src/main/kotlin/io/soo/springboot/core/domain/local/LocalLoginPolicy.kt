package io.soo.springboot.core.domain.local

import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class LocalLoginPolicy(
    private val userRepository: UserRepository,
    private val localCredentialRepository: LocalCredentialRepository,
) {
    @Transactional
    fun failByEmail(normalizedEmail: String, now: LocalDateTime = LocalDateTime.now()): FailResult {
        val user = userRepository.findByEmail(normalizedEmail) ?: return FailResult.NOT_FOUND
        val cred = localCredentialRepository.lockByUserId(user.id) ?: return FailResult.NOT_FOUND
        cred.recordLoginFailure(
            now = now,
            maxAttempts = 5,
            lockMinutes = 5,
        )
        localCredentialRepository.save(cred)
        return if (cred.isLocked(now)) FailResult.LOCKED else FailResult.BAD_CREDENTIALS
    }

    @Transactional
    fun successByUser(userId: Long) {
        val cred = localCredentialRepository.lockByUserId(userId) ?: return
        cred.recordLoginSuccess()
        localCredentialRepository.save(cred)
    }

    enum class FailResult { NOT_FOUND, BAD_CREDENTIALS, LOCKED }
}