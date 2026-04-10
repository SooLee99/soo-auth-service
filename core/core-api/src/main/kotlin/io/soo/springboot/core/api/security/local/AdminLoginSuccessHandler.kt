package io.soo.springboot.core.api.security.local

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.core.api.security.auth.UserIdResolver
import io.soo.springboot.core.api.security.response.SecurityErrorResponseWriter
import io.soo.springboot.core.api.security.token.AuthTokenManager
import io.soo.springboot.core.api.security.userdetails.UserPrincipal
import io.soo.springboot.core.domain.login.LoginHistoryService
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.LoginType
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class AdminLoginSuccessHandler(
    private val authTokenManager: AuthTokenManager,
    private val objectMapper: ObjectMapper,
    private val userIdResolver: UserIdResolver,
    private val loginHistoryService: LoginHistoryService,
    private val securityErrorResponseWriter: SecurityErrorResponseWriter,
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val ip = request.remoteAddr
        val ua = request.getHeader("User-Agent")
        val deviceId = request.getHeader("X-Device-Id")?.trim().orEmpty()
        val userId = userIdResolver.resolve(authentication)
        val principal = authentication.principal as UserPrincipal

        val isAdmin = authentication.authorities.any { it.authority == "ROLE_ADMIN" }
        if (!isAdmin) {
            loginHistoryService.recordLoginFailure(
                userId = userId,
                userEmail = principal.email,
                loginType = LoginType.LOCAL,
                ipAddress = ip,
                userAgent = ua,
                deviceId = deviceId,
                failureReason = "ADMIN_ROLE_REQUIRED",
            )
            securityErrorResponseWriter.writeError(
                response = response,
                request = request,
                type = ErrorType.FORBIDDEN,
                fields = mapOf("reason" to "ADMIN_ROLE_REQUIRED"),
            )
            return
        }

        val tokens = authTokenManager.issue(authentication, userId, deviceId, AuthProvider.LOCAL)

        loginHistoryService.recordLoginSuccess(
            userId = userId,
            userEmail = principal.email,
            loginType = LoginType.LOCAL,
            ipAddress = ip,
            userAgent = ua,
            deviceId = deviceId,
        )

        response.contentType = "application/json;charset=UTF-8"
        response.writer.write(
            objectMapper.writeValueAsString(
                ApiResponse.success(req = request, data = tokens),
            ),
        )
    }
}
