package io.soo.springboot.core.api.security.entrypoint

import io.soo.springboot.core.api.security.response.SecurityErrorFields
import io.soo.springboot.core.api.security.response.SecurityErrorResponseWriter
import io.soo.springboot.core.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class UnauthorizedEntryPoint(
    private val writer: SecurityErrorResponseWriter,
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        writer.writeError(
            response = response,
            request = request,
            type = ErrorType.UNAUTHORIZED,
            fields = SecurityErrorFields.authRequired(),
        )
    }
}
