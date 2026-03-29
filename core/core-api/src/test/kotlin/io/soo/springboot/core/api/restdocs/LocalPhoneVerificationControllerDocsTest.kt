package io.soo.springboot.core.api.restdocs

import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.core.api.controller.v1.LocalPhoneVerificationController
import io.soo.springboot.core.api.controller.v1.request.PhoneVerificationConfirmRequest
import io.soo.springboot.core.api.controller.v1.request.PhoneVerificationIssueRequest
import io.soo.springboot.core.domain.local.phone.PhoneConfirm
import io.soo.springboot.core.domain.local.phone.PhoneIssue
import io.soo.springboot.core.domain.local.phone.PhoneVerifyService
import io.soo.springboot.test.api.RestDocsTest
import io.soo.springboot.test.api.RestDocsUtils
import io.soo.springboot.test.api.mockMvcDocument
import io.soo.springboot.test.api.requestFields
import io.soo.springboot.test.api.responseFields
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath

class LocalPhoneVerificationControllerDocsTest : RestDocsTest() {

    private val phoneVerificationService = mockk<PhoneVerifyService>()

    @BeforeEach
    fun init() {
        mockMvc = mockController(LocalPhoneVerificationController(phoneVerificationService))
    }

    @Test
    fun requestVerification() {
        every { phoneVerificationService.issue(any()) } returns PhoneIssue(
            verificationId = "verification-id-001",
            expiresInSec = 180,
        )

        val responseDescriptors =
            ApiResponseFieldDescriptors.successCommon() + listOf(
                fieldWithPath("data").type(JsonFieldType.OBJECT).description("인증 요청 결과"),
                fieldWithPath("data.verificationId").type(JsonFieldType.STRING).description("인증 요청 식별자"),
                fieldWithPath("data.expiresInSec").type(JsonFieldType.NUMBER).description("인증번호 만료까지 남은 시간(초)"),
            )

        given()
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .body(PhoneVerificationIssueRequest(phoneNumber = "+82 10-1234-5678"))
            .`when`()
            .post("/api/v1/auth/local/phone-verifications/request")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-local-phone-verification-request",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestFields(
                        fieldWithPath("phoneNumber").type(JsonFieldType.STRING).description("인증 요청할 휴대폰 번호"),
                    ),
                    responseFields(*responseDescriptors.toTypedArray()),
                )
            )
    }

    @Test
    fun confirmVerification() {
        every { phoneVerificationService.confirm(any(), any(), any()) } returns PhoneConfirm(
            proofToken = "verified-phone-token",
            expiresInSec = 600,
        )

        val responseDescriptors =
            ApiResponseFieldDescriptors.successCommon() + listOf(
                fieldWithPath("data").type(JsonFieldType.OBJECT).description("인증 확인 결과"),
                fieldWithPath("data.phoneVerificationToken").type(JsonFieldType.STRING).description("회원가입에 사용할 휴대폰 인증 토큰"),
                fieldWithPath("data.expiresInSec").type(JsonFieldType.NUMBER).description("인증 토큰 만료까지 남은 시간(초)"),
            )

        given()
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .body(
                PhoneVerificationConfirmRequest(
                    phoneNumber = "+82 10-1234-5678",
                    verificationId = "verification-id-001",
                    code = "123456",
                )
            )
            .`when`()
            .post("/api/v1/auth/local/phone-verifications/confirm")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-local-phone-verification-confirm",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestFields(
                        fieldWithPath("phoneNumber").type(JsonFieldType.STRING).description("인증 확인할 휴대폰 번호"),
                        fieldWithPath("verificationId").type(JsonFieldType.STRING).description("인증 요청 식별자"),
                        fieldWithPath("code").type(JsonFieldType.STRING).description("6자리 인증번호"),
                    ),
                    responseFields(*responseDescriptors.toTypedArray()),
                )
            )
    }
}
