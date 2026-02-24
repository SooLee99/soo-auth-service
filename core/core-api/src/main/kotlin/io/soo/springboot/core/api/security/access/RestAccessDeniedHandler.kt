package io.soo.springboot.core.api.security.access

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class RestAccessDeniedHandler(
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        val type = ErrorType.FORBIDDEN

        response.status = type.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        response.setHeader("Cache-Control", "no-store")

        objectMapper.writeValue(
            response.outputStream,
            ApiResponse.error(
                type = type,
                req = request,
                fields = mapOf("reason" to "ACCESS_DENIED"),
            )
        )
    }
}
