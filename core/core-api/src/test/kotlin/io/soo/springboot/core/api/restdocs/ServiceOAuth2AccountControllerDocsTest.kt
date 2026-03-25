package io.soo.springboot.core.api.restdocs

import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.core.api.controller.v1.ServiceOAuth2AccountController
import io.soo.springboot.core.domain.admin.ServiceContextResolver
import io.soo.springboot.core.enums.ServiceStatus
import io.soo.springboot.storage.db.core.Service
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

class ServiceOAuth2AccountControllerDocsTest : RestDocsTest() {

    private val serviceContextResolver = mockk<ServiceContextResolver>()

    @BeforeEach
    fun init() {
        every { serviceContextResolver.resolveActive(any()) } returns sampleService()
        mockMvc = mockController(ServiceOAuth2AccountController(serviceContextResolver))
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
            .get("/api/v1/services/{serviceCode}/auth/oauth2/{provider}/authorize-url", "SHOP", "kakao")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-service-oauth2-authorize-url",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("X-Device-Id").optional().description("디바이스 식별자 (선택)"),
                    ),
                    pathParameters(
                        parameterWithName("serviceCode").description("서비스 코드 (예: SHOP, CRM, DEFAULT)"),
                        parameterWithName("provider").description("OAuth2 공급자 (예: kakao, naver, google)"),
                    ),
                    queryParameters(
                        parameterWithName("returnUrl").optional().description("로그인 완료 후 리다이렉트할 상대 경로"),
                    ),
                    responseFields(*responseDescriptors.toTypedArray()),
                )
            )
    }

    private fun sampleService(): Service =
        Service(
            id = 10L,
            serviceCode = "SHOP",
            serviceName = "Shopping Service",
            serviceStatus = ServiceStatus.ACTIVE,
        )
}
