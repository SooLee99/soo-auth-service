package io.soo.springboot.core.domain

import io.soo.springboot.core.enums.LoginStatus
import io.soo.springboot.core.enums.LoginType
import io.soo.springboot.storage.db.core.LoginHistory
import io.soo.springboot.storage.db.core.LoginHistoryRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class LoginHistoryService(
    private val loginHistoryRepository: LoginHistoryRepository,
) {

    fun recordLoginSuccess(
        userId: Long,
        userEmail: String,
        loginType: LoginType,
        ipAddress: String?,
        userAgent: String?,
        deviceId: String?,
    ): LoginHistory {
        return loginHistoryRepository.save(
            userId = userId,
            userEmail = userEmail,
            loginType = loginType,
            status = LoginStatus.SUCCESS,
            ipAddress = ipAddress,
            userAgent = userAgent,
            deviceId = deviceId,
            failureReason = null,
        )
    }

    fun recordLoginFailure(
        userId: Long,
        userEmail: String,
        loginType: LoginType,
        ipAddress: String?,
        userAgent: String?,
        deviceId: String?,
        failureReason: String?,
    ): LoginHistory {
        return loginHistoryRepository.save(
            userId = userId,
            userEmail = userEmail,
            loginType = loginType,
            status = LoginStatus.FAILURE,
            ipAddress = ipAddress,
            userAgent = userAgent,
            deviceId = deviceId,
            failureReason = failureReason,
        )
    }

    fun findByUserId(userId: Long, pageable: Pageable): Page<LoginHistory> {
        return loginHistoryRepository.findByUserId(userId, pageable)
    }

    fun findByUserIdAndDateRange(
        userId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        pageable: Pageable
    ): Page<LoginHistory> {
        return loginHistoryRepository.findByUserIdAndDateRange(userId, startDate, endDate, pageable)
    }
}
