package io.soo.springboot.core.api.security.local

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.core.api.security.auth.UserIdResolver
import io.soo.springboot.core.api.security.response.SecurityErrorResponseWriter
import io.soo.springboot.core.api.security.token.AuthTokenManager
import io.soo.springboot.core.api.security.userdetails.UserPrincipal
import io.soo.springboot.core.domain.authmethod.AuthMethodConfigService
import io.soo.springboot.core.domain.login.LoginHistoryService
import io.soo.springboot.core.enums.AuthMethod
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
class LocalLoginSuccessHandler(
    private val authTokenManager: AuthTokenManager,
    private val objectMapper: ObjectMapper,
    private val userIdResolver: UserIdResolver,
    private val loginHistoryService: LoginHistoryService,
    private val authMethodConfigService: AuthMethodConfigService,
    private val securityErrorResponseWriter: SecurityErrorResponseWriter,
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        if (!authMethodConfigService.isEnabled(AuthMethod.EMAIL)) {
            securityErrorResponseWriter.writeError(
                response = response,
                request = request,
                type = ErrorType.AUTH_METHOD_DISABLED,
                fields = mapOf("method" to AuthMethod.EMAIL.name),
            )
            return
        }

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
