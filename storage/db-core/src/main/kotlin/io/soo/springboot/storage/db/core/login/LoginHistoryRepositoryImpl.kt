package io.soo.springboot.storage.db.core.login

import io.soo.springboot.core.enums.LoginStatus
import io.soo.springboot.core.enums.LoginType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class LoginHistoryRepositoryImpl(
    private val jpaRepository: LoginHistoryJpaRepository,
) : LoginHistoryRepository {

    override fun save(
        userId: Long,
        userEmail: String,
        loginType: LoginType,
        status: LoginStatus,
        ipAddress: String?,
        userAgent: String?,
        deviceId: String?,
        failureReason: String?,
    ): LoginHistory {
        val entity = LoginHistoryEntity(
            userId = userId,
            userEmail = userEmail,
            loginType = loginType,
            loginStatus = status,
            ipAddress = ipAddress,
            userAgent = userAgent,
            deviceId = deviceId,
            failureReason = failureReason,
        )
        return LoginHistory.from(jpaRepository.save(entity))
    }

    override fun findByUserId(userId: Long, pageable: Pageable): Page<LoginHistory> {
        return jpaRepository.findByUserId(userId, pageable).map { LoginHistory.from(it) }
    }

    override fun findByUserIdAndDateRange(
        userId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        pageable: Pageable
    ): Page<LoginHistory> {
        return jpaRepository.findByUserIdAndCreatedAtBetween(userId, startDate, endDate, pageable)
            .map { LoginHistory.from(it) }
    }
}
