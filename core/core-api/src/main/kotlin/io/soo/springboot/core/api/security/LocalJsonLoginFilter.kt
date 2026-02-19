package io.soo.springboot.core.api.security

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import io.soo.springboot.core.api.controller.v1.request.LoginRequest
import io.soo.springboot.core.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

class LocalJsonLoginFilter(
    private val objectMapper: ObjectMapper,
) : UsernamePasswordAuthenticationFilter() {
    companion object {
        const val ATTR_NORMALIZED_EMAIL = "ATTR_NORMALIZED_EMAIL"
        const val ATTR_AUTH_ERROR = "ATTR_AUTH_ERROR"
    }

    data class AuthErrorPayload(
        val type: ErrorType,
        val userMessage: String,
        val detail: String? = null,
        val extra: Map<String, Any?> = emptyMap(),
    )

    override fun attemptAuthentication(request: HttpServletRequest, response: HttpServletResponse): Authentication {
        // 1) JSON 요청인지 확인
        validateJsonContentType(request)

        // 2) 바디 읽기 + 비어있는지 확인
        val body = readRequestBody(request)

        // 3) JSON 파싱 + 필수값 검증 + 정규화
        val (normalizedEmail, password) = validateAndNormalize(
            parseLoginRequest(request, body),
            request
        )

        // 4) AuthenticationManager로 인증 위임
        return authenticate(normalizedEmail, password, request)
    }

    // Step 1) Content-Type 검증
    private fun validateJsonContentType(request: HttpServletRequest) {
        val contentType = request.contentType.orEmpty()

        // contentType이 아예 없는 경우는 허용
        if (contentType.isNotBlank() && !contentType.startsWith(MediaType.APPLICATION_JSON_VALUE)) {
            fail(
                request = request,
                type = ErrorType.UNSUPPORTED_MEDIA_TYPE,
                userMessage = "지원하지 않는 Content-Type 입니다. application/json으로 요청해 주세요.",
                detail = "contentType=$contentType",
            )
        }
    }

    // Step 2) Body 읽기
    private fun readRequestBody(request: HttpServletRequest): String {
        val body = request.reader.use { it.readText() }
        if (body.isBlank()) {
            fail(
                request = request,
                type = ErrorType.INVALID_REQUEST_BODY,
                userMessage = "요청 본문이 비어 있습니다. email/password가 포함된 JSON을 전송해 주세요.",
                detail = "empty body",
            )
        }
        return body
    }

    // Step 3) JSON 파싱
    private fun parseLoginRequest(request: HttpServletRequest, body: String): LoginRequest {
        return try {
            objectMapper.readValue(body, LoginRequest::class.java)
        } catch (e: JsonParseException) {
            fail(
                request = request,
                type = ErrorType.INVALID_REQUEST_BODY,
                userMessage = "요청 본문(JSON) 형식이 올바르지 않습니다. 마지막 콤마(,) 등 문법을 확인해 주세요.",
                detail = e.message,
                extra = mapOf("cause" to e.javaClass.simpleName),
            )
        } catch (e: InvalidFormatException) {
            val valueText = e.value?.toString() ?: "null"
            fail(
                request = request,
                type = ErrorType.INVALID_REQUEST_BODY,
                userMessage = "요청 값 형식이 올바르지 않습니다. 입력값: '$valueText'",
                detail = e.message,
                extra = mapOf("cause" to e.javaClass.simpleName),
            )
        } catch (e: MismatchedInputException) {
            fail(
                request = request,
                type = ErrorType.INVALID_REQUEST_BODY,
                userMessage = "요청 JSON 형식이 올바르지 않습니다. 'email'과 'password'가 필요합니다.",
                detail = e.message,
                extra = mapOf("cause" to e.javaClass.simpleName),
            )
        } catch (e: JsonProcessingException) {
            fail(
                request = request,
                type = ErrorType.INVALID_REQUEST_BODY,
                userMessage = "요청 본문(JSON)을 해석할 수 없습니다.",
                detail = e.message,
                extra = mapOf("cause" to e.javaClass.simpleName),
            )
        }
    }

    // Step 4) 필수값 검증 + 정규화
    private fun validateAndNormalize(req: LoginRequest, request: HttpServletRequest): Pair<String, String> {
        val email = req.email.trim()
        val password = req.password.trim()

        when {
            email.isBlank() && password.isBlank() ->
                fail(request, ErrorType.INVALID_REQUEST_BODY, "'email'과 'password'는 필수입니다.", "blank email & password")

            email.isBlank() ->
                fail(request, ErrorType.INVALID_REQUEST_BODY, "'email'은(는) 필수입니다.", "blank email")

            password.isBlank() ->
                fail(request, ErrorType.INVALID_REQUEST_BODY, "'password'는(은) 필수입니다.", "blank password")
        }

        val normalizedEmail = email.lowercase()
        request.setAttribute(ATTR_NORMALIZED_EMAIL, normalizedEmail)

        return normalizedEmail to password
    }

    // Step 5) 인증 위임
    private fun authenticate(email: String, password: String, request: HttpServletRequest): Authentication {
        val authRequest = UsernamePasswordAuthenticationToken(email, password)
        setDetails(request, authRequest)
        return authenticationManager.authenticate(authRequest)
    }

    // 공통 실패 처리
    private fun fail(
        request: HttpServletRequest,
        type: ErrorType,
        userMessage: String,
        detail: String? = null,
        extra: Map<String, Any?> = emptyMap(),
    ): Nothing {
        val error = AuthErrorPayload(type = type, userMessage = userMessage, detail = detail, extra = extra)
        request.setAttribute(
            ATTR_AUTH_ERROR,
            error,
        )
        System.out.println("LocalJsonLoginFilter.fail: $error")
        throw AuthenticationServiceException(userMessage)
    }
}