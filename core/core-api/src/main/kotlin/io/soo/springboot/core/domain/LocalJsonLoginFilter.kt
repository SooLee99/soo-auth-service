package io.soo.springboot.core.domain

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

import io.soo.springboot.core.support.error.ErrorType

class LocalJsonLoginFilter(
    private val objectMapper: ObjectMapper,
) : UsernamePasswordAuthenticationFilter() {

    companion object {
        const val ATTR_NORMALIZED_EMAIL = "ATTR_NORMALIZED_EMAIL"
        const val HEADER_DEVICE_ID = "X-Device-Id"
        const val ATTR_AUTH_ERROR = "ATTR_AUTH_ERROR"
    }

    data class LoginRequest(val email: String?, val password: String?)

    data class AuthErrorPayload(
        val type: ErrorType,
        val userMessage: String,
        val detail: String? = null,
        val extra: Map<String, Any?> = emptyMap(),
    )

    override fun attemptAuthentication(request: HttpServletRequest, response: HttpServletResponse): Authentication {
        fun fail(type: ErrorType, userMessage: String, detail: String? = null, extra: Map<String, Any?> = emptyMap()): Nothing {
            request.setAttribute(ATTR_AUTH_ERROR, AuthErrorPayload(type, userMessage, detail, extra))
            throw AuthenticationServiceException(userMessage)
        }

        // 0) Content-Type 체크
        val contentType = request.contentType ?: ""
        if (contentType.isNotBlank() && !contentType.startsWith(MediaType.APPLICATION_JSON_VALUE)) {
            fail(
                type = ErrorType.UNSUPPORTED_MEDIA_TYPE,
                userMessage = "지원하지 않는 Content-Type 입니다. application/json으로 요청해 주세요.",
                detail = "contentType=$contentType",
            )
        }

        // 1) 바디 읽기
        val body = request.reader.use { it.readText() }
        if (body.isBlank()) {
            fail(
                type = ErrorType.INVALID_REQUEST_BODY,
                userMessage = "요청 본문이 비어 있습니다. email/password가 포함된 JSON을 전송해 주세요.",
                detail = "empty body",
            )
        }

        // 2) JSON 파싱
        val req = try {
            objectMapper.readValue(body, LoginRequest::class.java)
        } catch (e: JsonParseException) {
            fail(
                type = ErrorType.INVALID_REQUEST_BODY,
                userMessage = "요청 본문(JSON) 형식이 올바르지 않습니다. 마지막 콤마(,) 등 문법을 확인해 주세요.",
                detail = e.message,
                extra = mapOf("cause" to e.javaClass.simpleName),
            )
        } catch (e: InvalidFormatException) {
            val valueText = e.value?.toString() ?: "null"
            fail(
                type = ErrorType.INVALID_REQUEST_BODY,
                userMessage = "요청 값 형식이 올바르지 않습니다. 입력값: '$valueText'",
                detail = e.message,
                extra = mapOf("cause" to e.javaClass.simpleName),
            )
        } catch (e: MismatchedInputException) {
            fail(
                type = ErrorType.INVALID_REQUEST_BODY,
                userMessage = "요청 JSON 형식이 올바르지 않습니다. 'email'과 'password'가 필요합니다.",
                detail = e.message,
                extra = mapOf("cause" to e.javaClass.simpleName),
            )
        } catch (e: JsonProcessingException) {
            fail(
                type = ErrorType.INVALID_REQUEST_BODY,
                userMessage = "요청 본문(JSON)을 해석할 수 없습니다.",
                detail = e.message,
                extra = mapOf("cause" to e.javaClass.simpleName),
            )
        }

        // 3) 필수값 검증
        val email = req.email?.trim().orEmpty()
        val password = req.password?.trim().orEmpty()

        if (email.isBlank() && password.isBlank()) {
            fail(ErrorType.INVALID_REQUEST_BODY, "'email'과 'password'는 필수입니다.", detail = "blank email & password")
        }
        if (email.isBlank()) {
            fail(ErrorType.INVALID_REQUEST_BODY, "'email'은(는) 필수입니다.", detail = "blank email")
        }
        if (password.isBlank()) {
            fail(ErrorType.INVALID_REQUEST_BODY, "'password'는(은) 필수입니다.", detail = "blank password")
        }

        val normalizedEmail = email.lowercase()
        request.setAttribute(ATTR_NORMALIZED_EMAIL, normalizedEmail)

        val authRequest = UsernamePasswordAuthenticationToken(normalizedEmail, password)
        setDetails(request, authRequest)
        return authenticationManager.authenticate(authRequest)
    }
}
