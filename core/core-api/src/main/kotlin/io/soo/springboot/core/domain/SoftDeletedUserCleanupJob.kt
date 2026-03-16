package io.soo.springboot.core.domain

import io.soo.springboot.storage.db.core.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class SoftDeletedUserCleanupJob(
    private val userRepository: UserRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 5년 보관이 끝난 SOFT_DELETED 계정 물리 삭제 배치.
     * 기본은 매일 03:30 실행.
     */
    @Scheduled(cron = "\${app.user.soft-delete.cleanup-cron:0 30 3 * * *}")
    @Transactional
    fun purgeExpiredSoftDeletedUsers() {
        val deleted = userRepository.purgeSoftDeletedUsers(Instant.now())
        if (deleted > 0) {
            log.info("Purged expired soft-deleted users: {}", deleted)
        }
    }
}
