package io.soo.springboot.core.api.restdocs

import io.soo.springboot.core.api.controller.v1.OAuth2AccountController
import io.soo.springboot.test.api.RestDocsTest
import io.soo.springboot.test.api.RestDocsUtils
import io.soo.springboot.test.api.mockMvcDocument
import io.soo.springboot.test.api.pathParameters
import io.soo.springboot.test.api.queryParameters
import io.soo.springboot.test.api.requestHeaders
import io.soo.springboot.test.api.responseFields
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName

class OAuth2AccountControllerDocsTest : RestDocsTest() {

    @BeforeEach
    fun init() {
        mockMvc = mockController(OAuth2AccountController())
    }

    @Test
    fun authorizeUrl() {
        val responseDescriptors =
            ApiResponseFieldDescriptors.successCommon() + listOf(
                fieldWithPath("data").type(JsonFieldType.STRING)
                    .description("OAuth2 인가 URL 경로 (예: /oauth2/authorization/{provider})"),
            )

        given()
            .header("X-Device-Id", "device-001")
            .queryParam("returnUrl", "/app/callback")
            .`when`()
            .get("/api/v1/auth/oauth2/{provider}/authorize-url", "kakao")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-oauth2-authorize-url",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("X-Device-Id").optional().description("디바이스 식별자 (선택)"),
                    ),
                    pathParameters(
                        parameterWithName("provider").description("OAuth2 공급자 (예: kakao, naver, google)"),
                    ),
                    queryParameters(
                        parameterWithName("returnUrl").optional().description("로그인 완료 후 리다이렉트할 상대 경로"),
                    ),
                    responseFields(*responseDescriptors.toTypedArray()),
                ),
            )
    }
}
