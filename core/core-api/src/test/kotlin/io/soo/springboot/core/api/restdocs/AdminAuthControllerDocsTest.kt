package io.soo.springboot.core.api.restdocs

import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.core.api.controller.v1.AdminAuthController
import io.soo.springboot.core.api.security.auth.UserIdResolver
import io.soo.springboot.core.domain.LoginHistoryService
import io.soo.springboot.storage.db.core.LoginHistory
import io.soo.springboot.storage.db.core.LoginHistoryEntity
import io.soo.springboot.test.api.RestDocsTest
import io.soo.springboot.test.api.RestDocsUtils
import io.soo.springboot.test.api.mockMvcDocument
import io.soo.springboot.test.api.queryParameters
import io.soo.springboot.test.api.relaxedResponseFields
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
import org.springframework.security.authentication.TestingAuthenticationToken
import java.time.LocalDateTime

class AdminAuthControllerDocsTest : RestDocsTest() {

    private val loginHistoryService = mockk<LoginHistoryService>()
    private val userIdResolver = mockk<UserIdResolver>()
    private lateinit var controller: AdminAuthController

    @BeforeEach
    fun init() {
        controller = AdminAuthController(loginHistoryService, userIdResolver)
        mockMvc = mockController(controller)
    }

    @Test
    fun loginHistory() {
        val history = LoginHistory(
            id = 1L,
            userId = 1L,
            userEmail = "user@example.com",
            loginType = LoginHistoryEntity.LoginType.LOCAL,
            status = LoginHistoryEntity.LoginStatus.SUCCESS,
            ipAddress = "127.0.0.1",
            userAgent = "Mozilla/5.0",
            deviceId = "device-001",
            failureReason = null,
            createdAt = LocalDateTime.of(2025, 1, 1, 12, 0),
        )
        val page = PageImpl(listOf(history), PageRequest.of(0, 20), 1)

        every { userIdResolver.resolve(any()) } returns 1L
        every { loginHistoryService.findByUserId(any(), any()) } returns page

        val authentication = TestingAuthenticationToken("admin", "password", "ROLE_ADMIN")

        val responseDescriptors = listOf(
            // ApiResponse 공통
            fieldWithPath("result").type(JsonFieldType.STRING).description("결과"),
            fieldWithPath("meta").type(JsonFieldType.OBJECT).description("메타 정보"),
            fieldWithPath("meta.timestamp").type(JsonFieldType.STRING).description("응답 생성 시각"),
            fieldWithPath("meta.request").type(JsonFieldType.OBJECT).description("요청 정보"),
            fieldWithPath("meta.request.path").type(JsonFieldType.STRING).description("요청 경로"),
            fieldWithPath("meta.request.method").type(JsonFieldType.STRING).description("HTTP 메서드"),
            fieldWithPath("meta.request.query").type(JsonFieldType.VARIES).optional().description("쿼리 스트링(있으면 문자열, 없으면 null)"),
            fieldWithPath("meta.requestId").type(JsonFieldType.VARIES).optional().description("요청 ID(있으면 문자열)"),
            fieldWithPath("meta.durationMs").type(JsonFieldType.VARIES).optional().description("처리 시간(ms)"),
            fieldWithPath("meta.locale").type(JsonFieldType.VARIES).optional().description("로케일"),
            fieldWithPath("meta.paging").type(JsonFieldType.VARIES).optional().description("페이징 메타(있는 경우)"),

            // data(Page)
            fieldWithPath("data").type(JsonFieldType.OBJECT).description("로그인 이력 페이지(Page)"),

            fieldWithPath("data.content").type(JsonFieldType.ARRAY).description("로그인 이력 목록(Page.content)"),
            fieldWithPath("data.content[].id").type(JsonFieldType.NUMBER).description("이력 ID"),
            fieldWithPath("data.content[].userId").type(JsonFieldType.NUMBER).description("사용자 ID"),
            fieldWithPath("data.content[].userEmail").type(JsonFieldType.STRING).description("사용자 이메일"),
            fieldWithPath("data.content[].loginType").type(JsonFieldType.STRING).description("로그인 유형"),
            fieldWithPath("data.content[].status").type(JsonFieldType.STRING).description("로그인 상태"),
            fieldWithPath("data.content[].ipAddress").type(JsonFieldType.VARIES).optional().description("IP 주소(없으면 null)"),
            fieldWithPath("data.content[].userAgent").type(JsonFieldType.VARIES).optional().description("User-Agent(없으면 null)"),
            fieldWithPath("data.content[].deviceId").type(JsonFieldType.VARIES).optional().description("디바이스 ID(없으면 null)"),
            fieldWithPath("data.content[].failureReason").type(JsonFieldType.VARIES).optional().description("실패 사유(없으면 null)"),
            fieldWithPath("data.content[].createdAt").type(JsonFieldType.STRING).description("로그인 시각"),

            // Page 기본 필드
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
            fieldWithPath("error").type(JsonFieldType.VARIES).optional().description("에러 정보(실패 시에만 존재)"),
        )

        given()
            .auth().principal(authentication)
            .header("Authorization", "Bearer token")
            .queryParam("page", "0")
            .queryParam("size", "20")
            .`when`()
            .get("/api/v1/auth/admin/login-history")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-login-history",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("Authorization").description("Bearer 액세스 토큰"),
                    ),
                    queryParameters(
                        parameterWithName("page").optional().description("페이지 번호 (0부터 시작)"),
                        parameterWithName("size").optional().description("페이지 크기"),
                        parameterWithName("sort").optional().description("정렬 조건(예: createdAt,desc)"),
                        parameterWithName("startDate").optional().description("조회 시작 시각 (ISO-8601)"),
                        parameterWithName("endDate").optional().description("조회 종료 시각 (ISO-8601)"),
                    ),
                    relaxedResponseFields(*responseDescriptors.toTypedArray()),
                )
            )
    }
}