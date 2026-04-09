package io.soo.springboot.core.api.restdocs

import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.core.api.controller.v1.AuthAccountController
import io.soo.springboot.core.api.controller.v1.request.IdLoginRequest
import io.soo.springboot.core.api.controller.v1.request.IdSignUpRequest
import io.soo.springboot.core.api.controller.v1.request.PhoneLoginRequest
import io.soo.springboot.core.api.controller.v1.request.PhoneSignUpRequest
import io.soo.springboot.core.api.controller.v1.request.SignUpRequest
import io.soo.springboot.core.domain.id.IdAccountService
import io.soo.springboot.core.domain.id.IdLoginService
import io.soo.springboot.core.domain.local.LocalAccountService
import io.soo.springboot.core.domain.phone.account.LocalPhoneAccountService
import io.soo.springboot.core.domain.phone.login.LocalIssuedTokens
import io.soo.springboot.core.domain.phone.login.LocalPhoneLoginService
import io.soo.springboot.core.enums.Gender
import io.soo.springboot.test.api.RestDocsTest
import io.soo.springboot.test.api.RestDocsUtils
import io.soo.springboot.test.api.mockMvcDocument
import io.soo.springboot.test.api.requestFields
import io.soo.springboot.test.api.requestHeaders
import io.soo.springboot.test.api.responseFields
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath

class AuthAccountControllerDocsTest : RestDocsTest() {

    private val localAccountService = mockk<LocalAccountService>(relaxed = true)
    private val localPhoneAccountService = mockk<LocalPhoneAccountService>(relaxed = true)
    private val localPhoneLoginService = mockk<LocalPhoneLoginService>()
    private val idAccountService = mockk<IdAccountService>(relaxed = true)
    private val idLoginService = mockk<IdLoginService>()
    private lateinit var controller: AuthAccountController

    @BeforeEach
    fun init() {
        controller = AuthAccountController(
            localAccountService = localAccountService,
            localPhoneAccountService = localPhoneAccountService,
            localPhoneLoginService = localPhoneLoginService,
            idAccountService = idAccountService,
            idLoginService = idLoginService,
        )
        mockMvc = mockController(controller)
    }

    @Test
    fun emailSignup() {
        val request = SignUpRequest(
            email = "user@example.com",
            password = "P@ssw0rd!",
            name = "홍길동",
            nickname = "gildong",
            gender = Gender.MALE,
            phoneNumber = null,
            phoneVerificationToken = null,
            locale = "ko-KR",
            profileImageUrl = "https://cdn.example.com/profile.png",
            thumbnailImageUrl = "https://cdn.example.com/thumbnail.png",
            birthyear = "1990",
            birthday = "01-31",
        )

        given()
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .body(request)
            .`when`()
            .post("/api/v1/auth/local/email/signup")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-email-signup",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestFields(
                        fieldWithPath("email").type(JsonFieldType.STRING).description("이메일"),
                        fieldWithPath("password").type(JsonFieldType.STRING).description("비밀번호"),
                        fieldWithPath("name").type(JsonFieldType.STRING).optional().description("이름"),
                        fieldWithPath("nickname").type(JsonFieldType.STRING).optional().description("닉네임"),
                        fieldWithPath("gender").type(JsonFieldType.STRING).description("성별 (MALE/FEMALE/UNKNOWN)"),
                        fieldWithPath("phoneNumber").type(JsonFieldType.STRING).optional().description("휴대폰 번호 (선택)"),
                        fieldWithPath("phoneVerificationToken").type(JsonFieldType.STRING).optional()
                            .description("휴대폰 인증 완료 토큰 (선택)"),
                        fieldWithPath("locale").type(JsonFieldType.STRING).optional().description("로케일"),
                        fieldWithPath("profileImageUrl").type(JsonFieldType.STRING).optional().description("프로필 이미지 URL"),
                        fieldWithPath("thumbnailImageUrl").type(JsonFieldType.STRING).optional().description("썸네일 이미지 URL"),
                        fieldWithPath("birthyear").type(JsonFieldType.STRING).optional().description("출생연도 (yyyy)"),
                        fieldWithPath("birthday").type(JsonFieldType.STRING).optional().description("생일 (MM-DD)"),
                    ),
                ),
            )
    }

    @Test
    fun phoneSignup() {
        val request = PhoneSignUpRequest(
            phoneNumber = "+82 10-1234-5678",
            phoneVerificationToken = "verified-phone-token",
        )

        given()
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .body(request)
            .`when`()
            .post("/api/v1/auth/local/phone/signup")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-phone-signup",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestFields(
                        fieldWithPath("phoneNumber").type(JsonFieldType.STRING).description("휴대폰 번호"),
                        fieldWithPath("phoneVerificationToken").type(JsonFieldType.STRING).description("휴대폰 인증 완료 토큰"),
                    ),
                ),
            )
    }

    @Test
    fun phoneLogin() {
        every { localPhoneLoginService.login(any()) } returns LocalIssuedTokens(
            accessToken = "access-token",
            accessExpiresInSec = 3600,
            refreshToken = "refresh-token",
            refreshExpiresInSec = 1209600,
        )

        val responseDescriptors =
            ApiResponseFieldDescriptors.successCommon() + listOf(
                fieldWithPath("data").type(JsonFieldType.OBJECT).description("토큰 정보"),
                fieldWithPath("data.accessToken").type(JsonFieldType.STRING).description("액세스 토큰"),
                fieldWithPath("data.accessExpiresInSec").type(JsonFieldType.NUMBER).description("액세스 토큰 만료(초)"),
                fieldWithPath("data.refreshToken").type(JsonFieldType.STRING).description("리프레시 토큰"),
                fieldWithPath("data.refreshExpiresInSec").type(JsonFieldType.NUMBER).description("리프레시 토큰 만료(초)"),
            )

        given()
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .header("X-Device-Id", "device-001")
            .body(
                PhoneLoginRequest(
                    phoneNumber = "+821012345678",
                    phoneVerificationToken = "verified-phone-token",
                ),
            )
            .`when`()
            .post("/api/v1/auth/local/phone/login")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-phone-login",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("X-Device-Id").description("디바이스 식별자"),
                    ),
                    requestFields(
                        fieldWithPath("phoneNumber").type(JsonFieldType.STRING).description("휴대폰 번호"),
                        fieldWithPath("phoneVerificationToken").type(JsonFieldType.STRING).description("휴대폰 인증 완료 토큰"),
                    ),
                    responseFields(*responseDescriptors.toTypedArray()),
                ),
            )
    }

    @Test
    fun idSignup() {
        val request = IdSignUpRequest(
            loginId = "test-user",
            password = "P@ssw0rd!",
            phoneNumber = "+82 10-1234-5678",
            phoneVerificationToken = "verified-phone-token",
        )

        given()
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .body(request)
            .`when`()
            .post("/api/v1/auth/local/id/signup")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-id-signup",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestFields(
                        fieldWithPath("loginId").type(JsonFieldType.STRING).description("로그인 아이디"),
                        fieldWithPath("password").type(JsonFieldType.STRING).description("비밀번호"),
                        fieldWithPath("phoneNumber").type(JsonFieldType.STRING).description("휴대폰 번호"),
                        fieldWithPath("phoneVerificationToken").type(JsonFieldType.STRING).description("휴대폰 인증 완료 토큰"),
                    ),
                ),
            )
    }

    @Test
    fun idLogin() {
        every { idLoginService.login(any()) } returns LocalIssuedTokens(
            accessToken = "access-token",
            accessExpiresInSec = 3600,
            refreshToken = "refresh-token",
            refreshExpiresInSec = 1209600,
        )

        val responseDescriptors =
            ApiResponseFieldDescriptors.successCommon() + listOf(
                fieldWithPath("data").type(JsonFieldType.OBJECT).description("토큰 정보"),
                fieldWithPath("data.accessToken").type(JsonFieldType.STRING).description("액세스 토큰"),
                fieldWithPath("data.accessExpiresInSec").type(JsonFieldType.NUMBER).description("액세스 토큰 만료(초)"),
                fieldWithPath("data.refreshToken").type(JsonFieldType.STRING).description("리프레시 토큰"),
                fieldWithPath("data.refreshExpiresInSec").type(JsonFieldType.NUMBER).description("리프레시 토큰 만료(초)"),
            )

        given()
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .header("X-Device-Id", "device-001")
            .body(IdLoginRequest(loginId = "test-user", password = "P@ssw0rd!"))
            .`when`()
            .post("/api/v1/auth/local/id/login")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-id-login",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("X-Device-Id").description("디바이스 식별자"),
                    ),
                    requestFields(
                        fieldWithPath("loginId").type(JsonFieldType.STRING).description("로그인 아이디"),
                        fieldWithPath("password").type(JsonFieldType.STRING).description("비밀번호"),
                    ),
                    responseFields(*responseDescriptors.toTypedArray()),
                ),
            )
    }
}
