package io.soo.springboot.core.api.security.local

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.core.api.security.auth.UserIdResolver
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.api.security.token.AuthTokenManager
import io.soo.springboot.core.api.security.userdetails.UserPrincipal
import io.soo.springboot.core.domain.LoginHistoryService
import io.soo.springboot.core.support.response.ApiResponse
import io.soo.springboot.storage.db.core.LoginHistoryEntity
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class LocalLoginSuccessHandler(
    private val authTokenManager: AuthTokenManager,
    private val objectMapper: ObjectMapper,
    private val userIdResolver: UserIdResolver,
    private val loginHistoryService: LoginHistoryService,
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val ip = request.remoteAddr
        val ua = request.getHeader("User-Agent")
        val deviceId = request.getHeader("X-Device-Id")?.trim().orEmpty()
        val userId = userIdResolver.resolve(authentication)
        val principal = authentication.principal as UserPrincipal
        val tokens = authTokenManager.issue(authentication, userId, deviceId, AuthProvider.LOCAL)

        // 로그인 성공 기록
        loginHistoryService.recordLoginSuccess(
            userId = userId,
            userEmail = principal.email,
            loginType = LoginHistoryEntity.LoginType.LOCAL,
            ipAddress = ip,
            userAgent = ua,
            deviceId = deviceId,
        )

        response.contentType = "application/json;charset=UTF-8"
        response.writer.write(objectMapper.writeValueAsString(
            ApiResponse.success(req = request, data = tokens))
        )
    }
}
