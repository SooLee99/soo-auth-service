package io.soo.springboot.core.api.security.handler

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.core.domain.UserIdResolver
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.domain.token.JsonWebTokenService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class LocalLoginSuccessHandler(
    private val jsonWebTokenService: JsonWebTokenService,
    private val objectMapper: ObjectMapper,
    private val userIdResolver: UserIdResolver,
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val deviceId = request.getHeader("X-Device-Id")?.trim().orEmpty()
        require(deviceId.isNotBlank()) { "X-Device-Id header is required" }

        val userId = userIdResolver.resolve(authentication) // <- 너 프로젝트 방식대로 구현
        val tokens = jsonWebTokenService.issue(authentication, userId, deviceId, AuthProvider.LOCAL)

        response.contentType = "application/json;charset=UTF-8"
        response.writer.write(objectMapper.writeValueAsString(mapOf("result" to "OK", "data" to tokens)))
    }
}
