package io.soo.springboot.core.domain

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component

import io.soo.springboot.core.enums.AuthProvider

@Component
class OAuth2LoginFailureHandler(
    private val loginAttemptService: LoginAttemptService,
) : AuthenticationFailureHandler {

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        val session = request.getSession(false)
        val deviceId = (session?.getAttribute("DEVICE_ID") as? String)
            ?: request.getHeader("X-Device-Id")

        loginAttemptService.record(
            success = false,
            provider = AuthProvider.UNKNOWN,
            userId = null,
            providerUserId = null,
            deviceId = deviceId,
            ip = request.remoteAddr,
            userAgent = request.getHeader("User-Agent"),
            errorCode = exception.javaClass.simpleName,
            errorMessage = exception.message,
        )

        response.sendError(401, "OAuth login failed")
    }
}
