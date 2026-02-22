package io.soo.springboot.core.api.security.response

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component

// 인증이 필요한데(로그인 필요) 인증 정보가 없거나 유효하지 않을 때
@Component
class SecurityErrorResponseWriter(
    private val objectMapper: ObjectMapper,
) {
    fun writeError(
        response: HttpServletResponse,
        request: HttpServletRequest,
        type: ErrorType,
        message: String? = null,
        fields: Any? = null,
    ) {
        response.status = type.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        response.setHeader("Cache-Control", "no-store")

        val body = if (message == null) {
            ApiResponse.error(type = type, req = request, fields = fields)
        } else {
            ApiResponse.error(type = type, message = message, req = request, fields = fields)
        }

        objectMapper.writeValue(response.outputStream, body)
    }
}
