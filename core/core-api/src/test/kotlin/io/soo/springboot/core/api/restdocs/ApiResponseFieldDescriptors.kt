package io.soo.springboot.core.api.restdocs

import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath

object ApiResponseFieldDescriptors {
    fun successCommon(): List<FieldDescriptor> = listOf(
        fieldWithPath("result")
            .type(JsonFieldType.STRING)
            .description("요청 처리 결과 (SUCCESS/ERROR)"),
        fieldWithPath("meta.timestamp")
            .type(JsonFieldType.STRING)
            .description("응답 생성 시각 (서버 로컬 시간)"),
        fieldWithPath("meta.requestId")
            .type(JsonFieldType.STRING)
            .optional()
            .description("요청 추적 ID (있을 때만 반환)"),
        fieldWithPath("meta.durationMs")
            .type(JsonFieldType.NUMBER)
            .optional()
            .description("처리 시간(ms)"),
        fieldWithPath("meta.request.path")
            .type(JsonFieldType.STRING)
            .description("요청 경로"),
        fieldWithPath("meta.request.method")
            .type(JsonFieldType.STRING)
            .description("HTTP 메서드"),
        fieldWithPath("meta.request.query")
            .type(JsonFieldType.STRING)
            .optional()
            .description("쿼리 문자열 (없으면 null)"),
        fieldWithPath("meta.paging")
            .type(JsonFieldType.OBJECT)
            .optional()
            .description("페이징 메타 정보 (있을 때만 반환)"),
        fieldWithPath("meta.locale")
            .type(JsonFieldType.STRING)
            .optional()
            .description("응답 로케일 (있을 때만 반환)"),
        fieldWithPath("error")
            .type(JsonFieldType.OBJECT)
            .optional()
            .description("오류 정보 (에러 응답에서만 포함)"),
    )
}
