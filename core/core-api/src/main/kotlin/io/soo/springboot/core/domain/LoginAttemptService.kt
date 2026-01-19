package io.soo.springboot.core.domain

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.storage.db.core.LoginAttemptEntity
import io.soo.springboot.storage.db.core.LoginAttemptRepository

@Service
class LoginAttemptService(
    private val loginAttemptRepository: LoginAttemptRepository,
) {

    @Transactional
    fun record(
        success: Boolean,
        provider: AuthProvider,
        userId: Long?,
        providerUserId: String?,
        deviceId: String?,
        ip: String?,
        userAgent: String?,
        errorCode: String?,
        errorMessage: String?,
    ): LoginAttemptEntity {
        return loginAttemptRepository.save(
            LoginAttemptEntity(
                success = success,
                provider = provider,
                userId = userId,
                providerUserId = providerUserId,
                deviceId = deviceId,
                ip = ip,
                userAgent = userAgent,
                errorCode = errorCode,
                errorMessage = errorMessage,
            ),
        )
    }

    // 특정 유저의 최근 로그인 시도 조회
    fun recent(userId: Long): List<LoginAttemptEntity> =
        loginAttemptRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId)
}
