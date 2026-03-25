package io.soo.springboot.core.api.restdocs

import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.core.api.controller.v1.LocalAccountServiceController
import io.soo.springboot.core.api.controller.v1.request.RefreshRequest
import io.soo.springboot.core.api.controller.v1.request.SignUpRequest
import io.soo.springboot.core.api.controller.v1.request.PhoneSignUpRequest
import io.soo.springboot.core.api.controller.v1.request.WithdrawRequest
import io.soo.springboot.core.api.controller.v1.response.LogoutRequest
import io.soo.springboot.core.api.security.token.AuthTokenManager
import io.soo.springboot.core.api.security.token.IssuedTokens
import io.soo.springboot.core.domain.local.LocalAccountService
import io.soo.springboot.core.domain.admin.ServiceContextResolver
import io.soo.springboot.core.enums.Gender
import io.soo.springboot.core.enums.ServiceStatus
import io.soo.springboot.storage.db.core.Service
import io.soo.springboot.test.api.RestDocsTest
import io.soo.springboot.test.api.RestDocsUtils
import io.soo.springboot.test.api.mockMvcDocument
import io.soo.springboot.test.api.pathParameters
import io.soo.springboot.test.api.requestFields
import io.soo.springboot.test.api.requestHeaders
import io.soo.springboot.test.api.responseFields
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

class LocalAccountServiceControllerDocsTest : RestDocsTest() {

    private val localAccountService = mockk<LocalAccountService>(relaxed = true)
    private val authTokenManager = mockk<AuthTokenManager>()
    private val serviceContextResolver = mockk<ServiceContextResolver>()
    private lateinit var controller: LocalAccountServiceController

    @BeforeEach
    fun init() {
        controller = LocalAccountServiceController(localAccountService, authTokenManager, serviceContextResolver)
        mockMvc = mockController(controller)
        every { serviceContextResolver.resolveActive(any()) } returns sampleService()
    }

    @Test
    fun signUp() {
        val request = SignUpRequest(
            email = "user@example.com",
            password = "P@ssw0rd!",
            name = "홍길동",
            nickname = "gildong",
            gender = Gender.MALE,
            phoneNumber = "+82 10-1234-5678",
            phoneVerificationToken = "verified-phone-token",
            locale = "ko-KR",
            profileImageUrl = "https://cdn.example.com/profile.png",
            thumbnailImageUrl = "https://cdn.example.com/thumbnail.png",
            birthyear = "1990",
            birthday = "01-31",
        )

        val responseDescriptors =
            ApiResponseFieldDescriptors.successCommon() + listOf(
                fieldWithPath("data").type(JsonFieldType.OBJECT).description("회원가입 결과"),
                fieldWithPath("data.result").type(JsonFieldType.STRING).description("처리 결과(OK)"),
                fieldWithPath("data.serviceCode").type(JsonFieldType.STRING).description("적용 서비스 코드"),
            )

        given()
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .body(request)
            .`when`()
            .post("/api/v1/services/{serviceCode}/auth/local/signup", "SHOP")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-service-local-signup",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    pathParameters(
                        parameterWithName("serviceCode").description("서비스 코드 (예: SHOP, CRM, DEFAULT)"),
                    ),
                    requestFields(
                        fieldWithPath("email").type(JsonFieldType.STRING).description("이메일"),
                        fieldWithPath("password").type(JsonFieldType.STRING).description("비밀번호"),
                        fieldWithPath("name").type(JsonFieldType.STRING).optional().description("이름"),
                        fieldWithPath("nickname").type(JsonFieldType.STRING).optional().description("닉네임"),
                        fieldWithPath("gender").type(JsonFieldType.STRING).description("성별 (MALE/FEMALE)"),
                        fieldWithPath("phoneNumber").type(JsonFieldType.STRING).description("휴대폰 번호"),
                        fieldWithPath("phoneVerificationToken").type(JsonFieldType.STRING).description("휴대폰 인증 완료 토큰"),
                        fieldWithPath("locale").type(JsonFieldType.STRING).optional().description("로케일 (기본값: ko-KR)"),
                        fieldWithPath("profileImageUrl").type(JsonFieldType.STRING).optional().description("프로필 이미지 URL"),
                        fieldWithPath("thumbnailImageUrl").type(JsonFieldType.STRING).optional().description("썸네일 이미지 URL"),
                        fieldWithPath("birthyear").type(JsonFieldType.STRING).optional().description("출생연도 (yyyy)"),
                        fieldWithPath("birthday").type(JsonFieldType.STRING).optional().description("생일 (MM-DD)"),
                    ),
                    responseFields(*responseDescriptors.toTypedArray()),
                )
            )
    }

    @Test
    fun signUpByPhone() {
        val request = PhoneSignUpRequest(
            phoneNumber = "+82 10-1234-5678",
            phoneVerificationToken = "verified-phone-token",
        )

        val responseDescriptors =
            ApiResponseFieldDescriptors.successCommon() + listOf(
                fieldWithPath("data").type(JsonFieldType.OBJECT).description("회원가입 결과"),
                fieldWithPath("data.result").type(JsonFieldType.STRING).description("처리 결과(OK)"),
                fieldWithPath("data.serviceCode").type(JsonFieldType.STRING).description("적용 서비스 코드"),
            )

        given()
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .body(request)
            .`when`()
            .post("/api/v1/services/{serviceCode}/auth/local/signup/phone", "SHOP")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-service-local-signup-phone",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    pathParameters(
                        parameterWithName("serviceCode").description("서비스 코드 (예: SHOP, CRM, DEFAULT)"),
                    ),
                    requestFields(
                        fieldWithPath("phoneNumber").type(JsonFieldType.STRING).description("휴대폰 번호"),
                        fieldWithPath("phoneVerificationToken").type(JsonFieldType.STRING).description("휴대폰 인증 완료 토큰"),
                    ),
                    responseFields(*responseDescriptors.toTypedArray()),
                )
            )
    }

    @Test
    fun refreshToken() {
        every { authTokenManager.refresh(any(), any()) } returns IssuedTokens(
            accessToken = "access-token",
            accessExpiresInSec = 3600,
            refreshToken = "refresh-token",
            refreshExpiresInSec = 1209600,
        )

        val responseDescriptors =
            ApiResponseFieldDescriptors.successCommon() + listOf(
                fieldWithPath("data").type(JsonFieldType.OBJECT).description("토큰 재발급 결과"),
                fieldWithPath("data.serviceCode").type(JsonFieldType.STRING).description("적용 서비스 코드"),
                fieldWithPath("data.tokens").type(JsonFieldType.OBJECT).description("재발급 토큰"),
                fieldWithPath("data.tokens.accessToken").type(JsonFieldType.STRING).description("액세스 토큰"),
                fieldWithPath("data.tokens.accessExpiresInSec").type(JsonFieldType.NUMBER).description("액세스 토큰 만료(초)"),
                fieldWithPath("data.tokens.refreshToken").type(JsonFieldType.STRING).description("리프레시 토큰"),
                fieldWithPath("data.tokens.refreshExpiresInSec").type(JsonFieldType.NUMBER).description("리프레시 토큰 만료(초)"),
            )

        given()
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .header("X-Device-Id", "device-001")
            .body(RefreshRequest(refreshToken = "refresh-token"))
            .`when`()
            .post("/api/v1/services/{serviceCode}/auth/local/token/refresh", "SHOP")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-service-local-refresh",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    pathParameters(
                        parameterWithName("serviceCode").description("서비스 코드 (예: SHOP, CRM, DEFAULT)"),
                    ),
                    requestHeaders(
                        headerWithName("X-Device-Id").description("디바이스 식별자"),
                    ),
                    requestFields(
                        fieldWithPath("refreshToken").type(JsonFieldType.STRING).description("리프레시 토큰"),
                    ),
                    responseFields(*responseDescriptors.toTypedArray()),
                )
            )
    }

    @Test
    fun logout() {
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("uid", 1L)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)

        val responseDescriptors =
            ApiResponseFieldDescriptors.successCommon() + listOf(
                fieldWithPath("data").type(JsonFieldType.OBJECT).description("로그아웃 결과"),
                fieldWithPath("data.result").type(JsonFieldType.STRING).description("처리 결과(OK)"),
                fieldWithPath("data.serviceCode").type(JsonFieldType.STRING).description("적용 서비스 코드"),
            )

        try {
            given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .header("Authorization", "Bearer token")
                .header("X-Device-Id", "device-001")
                .body(LogoutRequest(refreshToken = "refresh-token", logoutAll = false))
                .`when`()
                .post("/api/v1/services/{serviceCode}/auth/local/logout", "SHOP")
                .then()
                .statusCode(200)
                .apply(
                    mockMvcDocument(
                        "auth-service-local-logout",
                        RestDocsUtils.requestPreprocessor(),
                        RestDocsUtils.responsePreprocessor(),
                        pathParameters(
                            parameterWithName("serviceCode").description("서비스 코드 (예: SHOP, CRM, DEFAULT)"),
                        ),
                        requestHeaders(
                            headerWithName("Authorization").description("Bearer 액세스 토큰"),
                            headerWithName("X-Device-Id").description("디바이스 식별자"),
                        ),
                        requestFields(
                            fieldWithPath("refreshToken").type(JsonFieldType.STRING).optional().description("리프레시 토큰 (단건 로그아웃)"),
                            fieldWithPath("logoutAll").type(JsonFieldType.BOOLEAN).optional().description("전체 로그아웃 여부"),
                        ),
                        responseFields(*responseDescriptors.toTypedArray()),
                    )
                )
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    @Test
    fun withdraw() {
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("uid", 1L)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)

        val responseDescriptors =
            ApiResponseFieldDescriptors.successCommon() + listOf(
                fieldWithPath("data").type(JsonFieldType.OBJECT).description("탈퇴 처리 결과"),
                fieldWithPath("data.result").type(JsonFieldType.STRING).description("처리 결과(OK)"),
                fieldWithPath("data.serviceCode").type(JsonFieldType.STRING).description("적용 서비스 코드"),
            )

        try {
            given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .header("Authorization", "Bearer token")
                .body(WithdrawRequest(reason = "privacy"))
                .`when`()
                .post("/api/v1/services/{serviceCode}/auth/local/withdraw", "SHOP")
                .then()
                .statusCode(200)
                .apply(
                    mockMvcDocument(
                        "auth-service-local-withdraw",
                        RestDocsUtils.requestPreprocessor(),
                        RestDocsUtils.responsePreprocessor(),
                        pathParameters(
                            parameterWithName("serviceCode").description("서비스 코드 (예: SHOP, CRM, DEFAULT)"),
                        ),
                        requestHeaders(
                            headerWithName("Authorization").description("Bearer 액세스 토큰"),
                        ),
                        requestFields(
                            fieldWithPath("reason").type(JsonFieldType.STRING).optional().description("탈퇴 사유"),
                        ),
                        responseFields(*responseDescriptors.toTypedArray()),
                    )
                )
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    private fun sampleService(): Service =
        Service(
            id = 10L,
            serviceCode = "SHOP",
            serviceName = "Shopping Service",
            serviceStatus = ServiceStatus.ACTIVE,
        )
}
