package io.soo.springboot.core.api.security

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.core.domain.LocalJsonLoginFilter
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class LocalLoginFailureHandler(
    private val objectMapper: ObjectMapper,
) : AuthenticationFailureHandler {

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        val payload = request.getAttribute(LocalJsonLoginFilter.ATTR_AUTH_ERROR)
                as? LocalJsonLoginFilter.AuthErrorPayload

        val (type, data) = when {
            // 1) 필터에서 fail()로 넣은 상세 정보가 있으면 최우선 사용
            payload != null -> payload.type to linkedMapOf(
                "userMessage" to payload.userMessage,
                "detail" to payload.detail,
                "extra" to payload.extra.takeIf { it.isNotEmpty() },
            ).filterValues { it != null }

            // 2) 계정 비활성
            exception is DisabledException -> ErrorType.ACCOUNT_DISABLED to null

            // 3) 아이디/비번 불일치(또는 사용자 없음이 숨겨져 BadCredentials로 들어옴)
            exception is BadCredentialsException -> ErrorType.INVALID_CREDENTIALS to null

            // 4) 그 외
            else -> ErrorType.UNAUTHORIZED to mapOf("cause" to exception.javaClass.simpleName)
        }

        response.status = type.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        response.setHeader("Cache-Control", "no-store")

        objectMapper.writeValue(
            response.outputStream,
            ApiResponse.error(type, request, data)
        )
    }
}
