package io.soo.springboot.core.api.restdocs

import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.core.api.controller.v1.AdminSmsController
import io.soo.springboot.core.api.controller.v1.request.AdminSmsSendReq
import io.soo.springboot.core.domain.SmsAdminService
import io.soo.springboot.core.domain.SmsStat
import io.soo.springboot.storage.db.core.SmsLog
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
import java.time.LocalDateTime

class AdminSmsControllerDocsTest : RestDocsTest() {

    private val smsAdminService = mockk<SmsAdminService>()
    private lateinit var controller: AdminSmsController

    @BeforeEach
    fun init() {
        controller = AdminSmsController(smsAdminService)
        mockMvc = mockController(controller)
    }

    @Test
    fun send() {
        val log = SmsLog(
            id = 2L,
            smsTo = "01011112222",
            smsFrom = "029302266",
            smsText = "관리자 공지 테스트",
            ok = true,
            provider = "SOLAPI",
            code = null,
            msg = null,
            createdAt = LocalDateTime.of(2026, 3, 28, 13, 0),
        )
        every { smsAdminService.send(any(), any()) } returns log

        given()
            .contentType("application/json")
            .header("Authorization", "Bearer token")
            .body(AdminSmsSendReq(to = "01011112222", text = "관리자 공지 테스트"))
            .`when`()
            .post("/api/v1/auth/admin/sms/send")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-sms-send",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("Authorization").description("Bearer 관리자 액세스 토큰"),
                    ),
                    io.soo.springboot.test.api.requestFields(
                        fieldWithPath("to").type(JsonFieldType.STRING).description("수신 번호"),
                        fieldWithPath("text").type(JsonFieldType.STRING).description("발송 메시지"),
                    ),
                    relaxedResponseFields(*sendResponseDescriptors().toTypedArray()),
                )
            )
    }

    @Test
    fun logs() {
        val log = SmsLog(
            id = 1L,
            smsTo = "01012345678",
            smsFrom = "029302266",
            smsText = "[soo-auth] 인증번호 [123456] (유효 3분)",
            ok = true,
            provider = "SOLAPI",
            code = null,
            msg = null,
            createdAt = LocalDateTime.of(2026, 3, 28, 12, 0),
        )
        val page = PageImpl(listOf(log), PageRequest.of(0, 20), 1)
        every { smsAdminService.logs(any(), any(), any()) } returns page

        given()
            .header("Authorization", "Bearer token")
            .queryParam("page", "0")
            .queryParam("size", "20")
            .`when`()
            .get("/api/v1/auth/admin/sms/logs")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-sms-logs",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("Authorization").description("Bearer 관리자 액세스 토큰"),
                    ),
                    queryParameters(
                        parameterWithName("page").optional().description("페이지 번호 (0부터 시작)"),
                        parameterWithName("size").optional().description("페이지 크기"),
                        parameterWithName("startDate").optional().description("조회 시작 시각 (ISO-8601)"),
                        parameterWithName("endDate").optional().description("조회 종료 시각 (ISO-8601)"),
                    ),
                    relaxedResponseFields(*logsResponseDescriptors().toTypedArray()),
                )
            )
    }

    @Test
    fun stats() {
        every { smsAdminService.stat(any(), any()) } returns SmsStat(
            total = 100,
            ok = 95,
            fail = 5,
            rate = 95.0,
        )

        given()
            .header("Authorization", "Bearer token")
            .`when`()
            .get("/api/v1/auth/admin/sms/stats")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-sms-stats",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("Authorization").description("Bearer 관리자 액세스 토큰"),
                    ),
                    queryParameters(
                        parameterWithName("startDate").optional().description("조회 시작 시각 (ISO-8601)"),
                        parameterWithName("endDate").optional().description("조회 종료 시각 (ISO-8601)"),
                    ),
                    relaxedResponseFields(*statsResponseDescriptors().toTypedArray()),
                )
            )
    }

    private fun logsResponseDescriptors() = listOf(
        *ApiResponseFieldDescriptors.successCommon().toTypedArray(),
        fieldWithPath("data").type(JsonFieldType.OBJECT).description("문자 발송 이력 페이지"),
        fieldWithPath("data.content").type(JsonFieldType.ARRAY).description("문자 발송 이력 목록"),
        fieldWithPath("data.content[].id").type(JsonFieldType.NUMBER).description("이력 ID"),
        fieldWithPath("data.content[].to").type(JsonFieldType.STRING).description("수신 번호"),
        fieldWithPath("data.content[].from").type(JsonFieldType.STRING).description("발신 번호"),
        fieldWithPath("data.content[].text").type(JsonFieldType.STRING).description("발송 메시지"),
        fieldWithPath("data.content[].ok").type(JsonFieldType.BOOLEAN).description("성공 여부"),
        fieldWithPath("data.content[].provider").type(JsonFieldType.STRING).description("발송 제공자"),
        fieldWithPath("data.content[].code").type(JsonFieldType.VARIES).optional().description("실패 코드(없으면 null)"),
        fieldWithPath("data.content[].msg").type(JsonFieldType.VARIES).optional().description("실패 메시지(없으면 null)"),
        fieldWithPath("data.content[].at").type(JsonFieldType.STRING).description("발송 시각"),
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

    private fun statsResponseDescriptors() = listOf(
        *ApiResponseFieldDescriptors.successCommon().toTypedArray(),
        fieldWithPath("data").type(JsonFieldType.OBJECT).description("문자 발송 통계"),
        fieldWithPath("data.total").type(JsonFieldType.NUMBER).description("전체 발송 수"),
        fieldWithPath("data.ok").type(JsonFieldType.NUMBER).description("성공 수"),
        fieldWithPath("data.fail").type(JsonFieldType.NUMBER).description("실패 수"),
        fieldWithPath("data.rate").type(JsonFieldType.NUMBER).description("성공률(%)"),
    )

    private fun sendResponseDescriptors() = listOf(
        *ApiResponseFieldDescriptors.successCommon().toTypedArray(),
        fieldWithPath("data").type(JsonFieldType.OBJECT).description("발송 결과"),
        fieldWithPath("data.id").type(JsonFieldType.NUMBER).description("이력 ID"),
        fieldWithPath("data.to").type(JsonFieldType.STRING).description("수신 번호"),
        fieldWithPath("data.from").type(JsonFieldType.STRING).description("발신 번호"),
        fieldWithPath("data.text").type(JsonFieldType.STRING).description("발송 메시지"),
        fieldWithPath("data.ok").type(JsonFieldType.BOOLEAN).description("성공 여부"),
        fieldWithPath("data.provider").type(JsonFieldType.STRING).description("발송 제공자"),
        fieldWithPath("data.at").type(JsonFieldType.STRING).description("발송 시각"),
    )
}
