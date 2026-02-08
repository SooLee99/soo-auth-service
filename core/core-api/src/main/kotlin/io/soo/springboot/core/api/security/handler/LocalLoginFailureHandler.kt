package io.soo.springboot.core.api.security.handler

import io.soo.springboot.core.api.security.response.ApiSecurityResponseWriter
import io.soo.springboot.core.api.security.response.SecurityErrorFields
import io.soo.springboot.core.domain.LocalJsonLoginFilter
import io.soo.springboot.core.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component

@Component
class LocalLoginFailureHandler(
    private val writer: ApiSecurityResponseWriter,
) : AuthenticationFailureHandler {

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        val payload = request.getAttribute(LocalJsonLoginFilter.ATTR_AUTH_ERROR)
                as? LocalJsonLoginFilter.AuthErrorPayload

        val (type, fields) = when {
            payload != null -> payload.type to linkedMapOf(
                "userMessage" to payload.userMessage,
                "detail" to payload.detail,
                "extra" to payload.extra.takeIf { it.isNotEmpty() },
            ).filterValues { it != null }

            exception is DisabledException ->
                ErrorType.ACCOUNT_DISABLED to SecurityErrorFields.accountDisabled()

            exception is BadCredentialsException ->
                ErrorType.INVALID_CREDENTIALS to SecurityErrorFields.badCredentials()

            else ->
                ErrorType.UNAUTHORIZED to mapOf("reason" to "UNAUTHORIZED")
        }

        writer.writeError(
            response = response,
            request = request,
            type = type,
            fields = fields,
        )
    }
}
