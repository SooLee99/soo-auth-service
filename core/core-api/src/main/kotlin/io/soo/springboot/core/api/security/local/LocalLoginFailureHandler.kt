package io.soo.springboot.core.api.security.local

import io.soo.springboot.core.api.security.response.SecurityErrorFields
import io.soo.springboot.core.api.security.response.SecurityErrorResponseWriter
import io.soo.springboot.core.domain.LocalLoginPolicyService
import io.soo.springboot.core.domain.LoginHistoryService
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.LoginHistoryEntity
import io.soo.springboot.storage.db.core.LoginHistoryRepository
import io.soo.springboot.storage.db.core.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class LocalLoginFailureHandler(
    private val writer: SecurityErrorResponseWriter,
    private val localLoginPolicyService: LocalLoginPolicyService,
    private val loginHistoryService: LoginHistoryService,
    private val userRepository: UserRepository,
) : AuthenticationFailureHandler {

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        val payload = request.getAttribute(LocalJsonLoginFilter.ATTR_AUTH_ERROR)
                as? LocalJsonLoginFilter.AuthErrorPayload

        val ip = request.remoteAddr
        val ua = request.getHeader("User-Agent")
        val deviceId = request.getHeader("X-Device-Id")?.trim().orEmpty()
        val normalizedEmail =
            request.getAttribute(LocalJsonLoginFilter.ATTR_NORMALIZED_EMAIL) as? String ?: "unknown"

        val result = localLoginPolicyService.recordFailureByEmail(
            normalizedEmail = normalizedEmail,
            now = LocalDateTime.now(),
        )

        // 로그인 실패 기록 (userId는 조회 가능할 때만)
        val user = userRepository.findByEmail(normalizedEmail)
        if (user != null) {
            val failureReason = when (result) {
                LocalLoginPolicyService.FailureResult.LOCKED -> "LOGIN_ATTEMPTS_EXCEEDED"
                LocalLoginPolicyService.FailureResult.BAD_CREDENTIALS -> "BAD_CREDENTIALS"
                LocalLoginPolicyService.FailureResult.NOT_FOUND -> "ACCOUNT_NOT_FOUND"
            }

            loginHistoryService.recordLoginFailure(
                userId = user.id,
                userEmail = normalizedEmail,
                loginType = LoginHistoryEntity.LoginType.LOCAL,
                ipAddress = ip,
                userAgent = ua,
                deviceId = deviceId,
                failureReason = failureReason,
            )
        }

        val (type, fields) = when {
            payload != null -> {
                payload.type to linkedMapOf(
                    "userMessage" to payload.userMessage,
                    "detail" to payload.detail,
                    "extra" to payload.extra.takeIf { it.isNotEmpty() },
                ).filterValues { it != null }
            }

            exception is DisabledException -> {
                ErrorType.ACCOUNT_DISABLED to SecurityErrorFields.accountDisabled()
            }

            // 로컬 로그인 실패 정책 결과 반영 (잠금/계정없음/비밀번호오류)
            result == LocalLoginPolicyService.FailureResult.LOCKED -> {
                ErrorType.LOGIN_ATTEMPTS_EXCEEDED to mapOf(
                    "reason" to "LOGIN_ATTEMPTS_EXCEEDED"
                )
            }

            exception is BadCredentialsException -> {
                when (result) {
                    LocalLoginPolicyService.FailureResult.BAD_CREDENTIALS -> ErrorType.LOGIN_BAD_CREDENTIALS
                    LocalLoginPolicyService.FailureResult.NOT_FOUND -> ErrorType.LOGIN_ACCOUNT_NOT_FOUND
                    else -> ErrorType.UNAUTHORIZED
                } to SecurityErrorFields.badCredentials()
            }

            else -> {
                ErrorType.UNAUTHORIZED to mapOf("reason" to "UNAUTHORIZED")
            }
        }

        writer.writeError(
            response = response,
            request = request,
            type = type,
            fields = fields,
        )
    }
}