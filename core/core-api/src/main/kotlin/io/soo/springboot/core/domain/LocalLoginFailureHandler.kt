package io.soo.springboot.core.domain

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component
import java.time.LocalDateTime

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.core.support.response.ApiResponse

@Component
class LocalLoginFailureHandler(
    private val localLoginPolicyService: LocalLoginPolicyService,
    private val loginAttemptService: LoginAttemptService,
    private val objectMapper: ObjectMapper,
) : AuthenticationFailureHandler {

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        val ip = request.remoteAddr
        val ua = request.getHeader("User-Agent")
        val deviceId = request.getHeader(LocalJsonLoginFilter.HEADER_DEVICE_ID)?.takeIf { it.isNotBlank() }
        val normalizedEmail = request.getAttribute(LocalJsonLoginFilter.ATTR_NORMALIZED_EMAIL) as? String ?: "unknown"

        // ✅ 1) 요청 바디 자체 문제 (빈 바디/JSON 파싱 실패) → 400, 잠금 카운트 올리지 않기 권장
        if (exception is AuthenticationServiceException) {
            response.status = 400
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = "UTF-8"

            val errorData = linkedMapOf<String, Any?>(
                "message" to "요청 본문이 올바르지 않습니다. JSON 형식을 확인해주세요.",
                "detail" to exception.message,
                "timestamp" to LocalDateTime.now(),
                "path" to request.requestURI,
                "method" to request.method,
                "query" to request.queryString,
            )

            val body = ApiResponse.error<Any>(ErrorType.INVALID_REQUEST_BODY, errorData)
            objectMapper.writeValue(response.outputStream, body)
            return
        }

        // ✅ 2) 여기부터는 기존 로직(비번 틀림/락 등) 유지
        val result = localLoginPolicyService.recordFailureByEmail(
            normalizedEmail = normalizedEmail,
            now = LocalDateTime.now(),
        )

        val (status, errorCode, message) = when (result) {
            LocalLoginPolicyService.FailureResult.LOCKED ->
                Triple(429, "LOCKED", "로그인 시도 횟수가 초과되었습니다. 잠시 후 다시 시도해 주세요.")
            LocalLoginPolicyService.FailureResult.BAD_CREDENTIALS ->
                Triple(401, "BAD_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.")
            LocalLoginPolicyService.FailureResult.NOT_FOUND ->
                Triple(401, "BAD_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.")
        }

        loginAttemptService.record(
            success = false,
            provider = AuthProvider.LOCAL,
            userId = null,
            providerUserId = normalizedEmail,
            deviceId = deviceId,
            ip = ip,
            userAgent = ua,
            errorCode = errorCode,
            errorMessage = exception.message,
        )

        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"

       objectMapper.writeValue(response.outputStream, ApiResponse.error<Any>(ErrorType.UNAUTHORIZED, mapOf("code" to errorCode, "message" to message)))
    }
}
