package io.soo.springboot.core.domain.token

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class JwtDenylistCleanupJob(
    private val denylistStore: JwtDenylistStore,
) {
    // 매 6시간마다 정리 (원하는 주기로 조정)
    @Scheduled(cron = "0 0 */6 * * *")
    fun cleanup() {
        denylistStore.purge(Instant.now())
    }
}
