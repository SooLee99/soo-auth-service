package io.soo.springboot.core.api.restdocs

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.core.api.controller.v1.HealthController
import io.soo.springboot.core.api.controller.v1.request.LoginRequest
import io.soo.springboot.core.api.security.auth.UserIdResolver
import io.soo.springboot.core.api.security.local.AdminLoginSuccessHandler
import io.soo.springboot.core.api.security.local.LocalJsonLoginFilter
import io.soo.springboot.core.api.security.response.SecurityErrorResponseWriter
import io.soo.springboot.core.api.security.token.AuthTokenManager
import io.soo.springboot.core.api.security.token.IssuedTokens
import io.soo.springboot.core.api.security.userdetails.UserPrincipal
import io.soo.springboot.core.domain.health.HealthSnapshotService
import io.soo.springboot.core.domain.login.LoginHistoryService
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.test.api.RestDocsTest
import io.soo.springboot.test.api.RestDocsUtils
import io.soo.springboot.test.api.mockMvcDocument
import io.soo.springboot.test.api.relaxedResponseFields
import io.soo.springboot.test.api.requestFields
import io.soo.springboot.test.api.requestHeaders
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken

class AdminLoginDocsTest : RestDocsTest() {

    private val objectMapper = jacksonObjectMapper()
        .findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)

    private val authTokenManager = mockk<AuthTokenManager>()
    private val userIdResolver = mockk<UserIdResolver>()
    private val loginHistoryService = mockk<LoginHistoryService>(relaxed = true)
    private val healthSnapshotService = mockk<HealthSnapshotService>()

    @BeforeEach
    fun init() {
        every { userIdResolver.resolve(any()) } returns 100L
        every { healthSnapshotService.publicSummary(any<HttpServletRequest>()) } returns mapOf(
            "status" to "UP",
            "application" to "core-api",
            "uptimeSec" to 10L,
        )
        every { authTokenManager.issue(any(), any(), any(), any()) } returns IssuedTokens(
            accessToken = "admin-access-token",
            accessExpiresInSec = 3600,
            refreshToken = "admin-refresh-token",
            refreshExpiresInSec = 1209600,
        )

        val principal = UserPrincipal(
            userId = 100L,
            email = "admin@example.com",
            passwordHash = null,
            role = "ADMIN",
            provider = AuthProvider.LOCAL,
        )

        val authenticationManager = AuthenticationManager { _ ->
            UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        }

        val successHandler = AdminLoginSuccessHandler(
            authTokenManager = authTokenManager,
            objectMapper = objectMapper,
            userIdResolver = userIdResolver,
            loginHistoryService = loginHistoryService,
            securityErrorResponseWriter = SecurityErrorResponseWriter(objectMapper),
        )

        val loginFilter = LocalJsonLoginFilter(objectMapper).apply {
            setAuthenticationManager(authenticationManager)
            setFilterProcessesUrl("/api/v1/auth/admin/login")
            setAuthenticationSuccessHandler(successHandler)
        }

        mockMvc = mockController(HealthController(healthSnapshotService), loginFilter)
    }

    @Test
    fun adminLogin() {
        val request = LoginRequest(
            email = "admin@example.com",
            password = "AdminP@ssw0rd!",
        )

        val responseDescriptors = listOf(
            fieldWithPath("result").type(JsonFieldType.STRING).description("결과"),
            fieldWithPath("meta").type(JsonFieldType.OBJECT).description("메타 정보"),
            fieldWithPath("meta.timestamp").type(JsonFieldType.STRING).description("응답 생성 시각"),
            fieldWithPath("meta.request").type(JsonFieldType.OBJECT).description("요청 정보"),
            fieldWithPath("meta.request.path").type(JsonFieldType.STRING).description("요청 경로"),
            fieldWithPath("meta.request.method").type(JsonFieldType.STRING).description("HTTP 메서드"),
            fieldWithPath("meta.request.query").type(JsonFieldType.VARIES).optional().description("쿼리 스트링(있으면 문자열, 없으면 null)"),
            fieldWithPath("data").type(JsonFieldType.OBJECT).description("토큰 정보"),
            fieldWithPath("data.accessToken").type(JsonFieldType.STRING).description("액세스 토큰"),
            fieldWithPath("data.accessExpiresInSec").type(JsonFieldType.NUMBER).description("액세스 토큰 만료(초)"),
            fieldWithPath("data.refreshToken").type(JsonFieldType.STRING).description("리프레시 토큰"),
            fieldWithPath("data.refreshExpiresInSec").type(JsonFieldType.NUMBER).description("리프레시 토큰 만료(초)"),
        )

        given()
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .header("X-Device-Id", "admin-device-001")
            .body(request)
            .`when`()
            .post("/api/v1/auth/admin/login")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-login",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("X-Device-Id").optional().description("디바이스 식별자 (선택)"),
                    ),
                    requestFields(
                        fieldWithPath("email").type(JsonFieldType.STRING).description("관리자 이메일"),
                        fieldWithPath("password").type(JsonFieldType.STRING).description("비밀번호"),
                    ),
                    relaxedResponseFields(*responseDescriptors.toTypedArray()),
                ),
            )
    }
}

