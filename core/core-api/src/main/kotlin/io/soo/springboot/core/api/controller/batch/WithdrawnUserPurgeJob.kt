package io.soo.springboot.core.api.controller.batch

import io.soo.springboot.storage.db.core.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class WithdrawnUserPurgeJob(
    private val userAccountRepository: UserAccountRepository,
    private val oauthIdentityRepository: OAuthIdentityRepository,
    private val localCredentialRepository: LocalCredentialRepository,
    private val userDeviceRepository: UserDeviceRepository,
    private val userSessionMapRepository: UserSessionMapRepository,
    private val loginAttemptRepository: LoginAttemptRepository,
) {

    @Value("\${app.withdraw.retention-days:30}")
    private var retentionDays: Long = 30

    /**
     * 매일 새벽 3시 실행(예시)
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    fun purge() {
        val cutoff = LocalDateTime.now().minusDays(retentionDays)
        val userIds = userAccountRepository.findUserIdsToPurge(cutoff)
        if (userIds.isEmpty()) return

        userIds.forEach { userId ->
            // 자식부터 삭제
            userSessionMapRepository.deleteAllByUserId(userId)
            userDeviceRepository.deleteAllByUserId(userId)
            oauthIdentityRepository.deleteAllByUserId(userId)
            localCredentialRepository.deleteByUserId(userId)
            loginAttemptRepository.deleteAllByUserId(userId)

            // 마지막에 user_account 삭제
            userAccountRepository.deleteById(userId)
        }
    }
}
