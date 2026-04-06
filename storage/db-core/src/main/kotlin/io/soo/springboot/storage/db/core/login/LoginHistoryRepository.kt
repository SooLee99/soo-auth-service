package io.soo.springboot.storage.db.core.login

import io.soo.springboot.core.enums.LoginStatus
import io.soo.springboot.core.enums.LoginType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

interface LoginHistoryRepository {
    fun save(
        userId: Long,
        userEmail: String,
        loginType: LoginType,
        status: LoginStatus,
        ipAddress: String?,
        userAgent: String?,
        deviceId: String?,
        failureReason: String?,
    ): LoginHistory

    fun findByUserId(userId: Long, pageable: Pageable): Page<LoginHistory>

    fun findByUserIdAndDateRange(
        userId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        pageable: Pageable
    ): Page<LoginHistory>
}
