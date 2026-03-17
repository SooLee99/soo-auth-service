package io.soo.springboot.core.api.restdocs

import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.core.api.controller.v1.AdminServiceController
import io.soo.springboot.core.api.controller.v1.request.AdminServiceCreateRequest
import io.soo.springboot.core.domain.AdminServiceManagementService
import io.soo.springboot.core.enums.ServiceStatus
import io.soo.springboot.storage.db.core.Service
import io.soo.springboot.test.api.RestDocsTest
import io.soo.springboot.test.api.RestDocsUtils
import io.soo.springboot.test.api.mockMvcDocument
import io.soo.springboot.test.api.pathParameters
import io.soo.springboot.test.api.queryParameters
import io.soo.springboot.test.api.relaxedResponseFields
import io.soo.springboot.test.api.requestFields
import io.soo.springboot.test.api.requestHeaders
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName

class AdminServiceControllerDocsTest : RestDocsTest() {

    private val adminServiceManagementService = mockk<AdminServiceManagementService>()
    private lateinit var controller: AdminServiceController

    @BeforeEach
    fun init() {
        controller = AdminServiceController(adminServiceManagementService)
        mockMvc = mockController(controller)
    }

    @Test
    fun registerService() {
        every { adminServiceManagementService.registerService(any(), any()) } returns sampleService()

        given()
            .contentType("application/json")
            .header("Authorization", "Bearer token")
            .body(AdminServiceCreateRequest(serviceCode = "SHOP", serviceName = "Shopping Service"))
            .`when`()
            .post("/api/v1/auth/admin/services")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-service-register",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("Authorization").description("Bearer 관리자 액세스 토큰"),
                    ),
                    requestFields(
                        fieldWithPath("serviceCode").type(JsonFieldType.STRING).description("서비스 코드"),
                        fieldWithPath("serviceName").type(JsonFieldType.STRING).description("서비스 이름"),
                    ),
                    relaxedResponseFields(*singleServiceResponseDescriptors().toTypedArray()),
                )
            )
    }

    @Test
    fun listServices() {
        val page = PageImpl(listOf(sampleService()), PageRequest.of(0, 20), 1)
        every { adminServiceManagementService.listServices(any()) } returns page

        given()
            .header("Authorization", "Bearer token")
            .queryParam("page", "0")
            .queryParam("size", "20")
            .`when`()
            .get("/api/v1/auth/admin/services")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-service-list",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("Authorization").description("Bearer 관리자 액세스 토큰"),
                    ),
                    queryParameters(
                        parameterWithName("page").optional().description("페이지 번호 (0부터 시작)"),
                        parameterWithName("size").optional().description("페이지 크기"),
                    ),
                    relaxedResponseFields(*pageServiceResponseDescriptors().toTypedArray()),
                )
            )
    }

    @Test
    fun deactivateService() {
        every { adminServiceManagementService.deactivateService(any()) } returns sampleService().copy(
            serviceStatus = ServiceStatus.INACTIVE,
        )

        given()
            .header("Authorization", "Bearer token")
            .`when`()
            .post("/api/v1/auth/admin/services/{serviceCode}/deactivate", "SHOP")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-service-deactivate",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("Authorization").description("Bearer 관리자 액세스 토큰"),
                    ),
                    pathParameters(
                        parameterWithName("serviceCode").description("비활성화 대상 서비스 코드"),
                    ),
                    relaxedResponseFields(*singleServiceResponseDescriptors().toTypedArray()),
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

    private fun singleServiceResponseDescriptors() = listOf(
        *ApiResponseFieldDescriptors.successCommon().toTypedArray(),
        fieldWithPath("data").type(JsonFieldType.OBJECT).description("서비스 정보"),
        fieldWithPath("data.serviceId").type(JsonFieldType.NUMBER).description("서비스 ID"),
        fieldWithPath("data.serviceCode").type(JsonFieldType.STRING).description("서비스 코드"),
        fieldWithPath("data.serviceName").type(JsonFieldType.STRING).description("서비스 이름"),
        fieldWithPath("data.serviceStatus").type(JsonFieldType.STRING).description("서비스 상태(ACTIVE/INACTIVE)"),
    )

    private fun pageServiceResponseDescriptors() = listOf(
        *ApiResponseFieldDescriptors.successCommon().toTypedArray(),
        fieldWithPath("data").type(JsonFieldType.OBJECT).description("서비스 목록 페이지"),
        fieldWithPath("data.content").type(JsonFieldType.ARRAY).description("서비스 목록"),
        fieldWithPath("data.content[].serviceId").type(JsonFieldType.NUMBER).description("서비스 ID"),
        fieldWithPath("data.content[].serviceCode").type(JsonFieldType.STRING).description("서비스 코드"),
        fieldWithPath("data.content[].serviceName").type(JsonFieldType.STRING).description("서비스 이름"),
        fieldWithPath("data.content[].serviceStatus").type(JsonFieldType.STRING).description("서비스 상태(ACTIVE/INACTIVE)"),
        fieldWithPath("data.totalElements").type(JsonFieldType.NUMBER).description("전체 건수"),
        fieldWithPath("data.totalPages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
        fieldWithPath("data.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
        fieldWithPath("data.number").type(JsonFieldType.NUMBER).description("현재 페이지 번호"),
        fieldWithPath("data.numberOfElements").type(JsonFieldType.NUMBER).description("현재 페이지 요소 수"),
        fieldWithPath("data.first").type(JsonFieldType.BOOLEAN).description("첫 페이지 여부"),
        fieldWithPath("data.last").type(JsonFieldType.BOOLEAN).description("마지막 페이지 여부"),
        fieldWithPath("data.empty").type(JsonFieldType.BOOLEAN).description("비어있는 페이지 여부"),
        subsectionWithPath("data.pageable").ignored(),
        subsectionWithPath("data.sort").ignored(),
    )
}
