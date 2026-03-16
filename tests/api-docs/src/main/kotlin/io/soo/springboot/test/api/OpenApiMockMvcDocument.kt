package io.soo.springboot.test.api

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler
import org.springframework.restdocs.operation.preprocess.OperationRequestPreprocessor
import org.springframework.restdocs.operation.preprocess.OperationResponsePreprocessor
import org.springframework.restdocs.snippet.Snippet

fun mockMvcDocument(
    identifier: String,
    requestPreprocessor: OperationRequestPreprocessor,
    responsePreprocessor: OperationResponsePreprocessor,
    vararg snippets: Snippet,
): RestDocumentationResultHandler {
    val ctx = OpenApiDocContext.consume()
    val meta = ApiDocCatalog.resolve(identifier)

    val description = buildString {
        appendLine(meta.description)
        appendLine()
        appendLine("### 인증")
        appendLine(meta.authMarkdown)
        appendLine()

        if (ctx.headers.isNotEmpty()) {
            appendLine("### 헤더")
            ctx.headers.forEach { h ->
                appendLine("- `${h.name}`: ${h.description}")
            }
            appendLine()
        }
        if (ctx.pathParams.isNotEmpty()) {
            appendLine("### Path 파라미터")
            ctx.pathParams.forEach { p ->
                appendLine("- `${p.name}`: ${p.description}")
            }
            appendLine()
        }
        if (ctx.queryParams.isNotEmpty()) {
            appendLine("### Query 파라미터")
            ctx.queryParams.forEach { p ->
                appendLine("- `${p.name}`: ${p.description}")
            }
            appendLine()
        }
        if (ctx.requestFields.isNotEmpty()) {
            appendLine("### 요청 바디 필드")
            ctx.requestFields.forEach { f ->
                appendLine("- `${f.path}`: ${f.description}")
            }
            appendLine()
        }
        if (ctx.responseFields.isNotEmpty()) {
            appendLine("### 응답 바디 필드")
            ctx.responseFields.forEach { f ->
                appendLine("- `${f.path}`: ${f.description}")
            }
            appendLine()
        }

        appendLine("### 공통 에러")
        appendLine("- **401** 인증 실패/만료/위조")
        appendLine("- **403** 권한 없음(권한 필요한 API)")
        appendLine("- **400** validation 실패(필드 형식/필수 누락)")
        appendLine()
        appendLine("> 실제 요청/응답 예시는 Swagger UI의 **Examples** 섹션에 테스트 실행 결과로 자동 포함됩니다.")
    }.trim()

    val resourceSnippet = resource(
        ResourceSnippetParameters.builder()
            .tag(meta.tag)
            .summary(meta.summary)
            .description(description)
            // ✅ 아래가 Swagger에 “테스트 코드 설명”이 반영되는 핵심
            .requestHeaders(*ctx.headers.toTypedArray())
            .pathParameters(*ctx.pathParams.toTypedArray())
            .queryParameters(*ctx.queryParams.toTypedArray())
            .requestFields(*ctx.requestFields.toTypedArray())
            .responseFields(*ctx.responseFields.toTypedArray())
            .build(),
    )

    return MockMvcRestDocumentationWrapper.document(
        identifier,
        requestPreprocessor,
        responsePreprocessor,
        resourceSnippet,
        *snippets,
    )
}

private object ApiDocCatalog {
    // 여기서 “촘촘한 한글 설명/요약/태그”만 관리하면 됩니다.
    private val map = mapOf(
        "auth-admin-login-history" to ApiMeta(
            tag = "Admin",
            summary = "관리자 로그인 이력 조회",
            description = "관리자 권한으로 로그인 이력을 페이징 조회합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-admin-user-block" to ApiMeta(
            tag = "Admin",
            summary = "관리자 사용자 차단",
            description = "관리자 권한으로 특정 사용자를 차단합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-admin-user-unblock" to ApiMeta(
            tag = "Admin",
            summary = "관리자 사용자 차단 해제",
            description = "관리자 권한으로 특정 사용자의 차단을 해제합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-admin-blocked-users" to ApiMeta(
            tag = "Admin",
            summary = "관리자 차단 사용자 목록 조회",
            description = "차단된 사용자 목록을 페이징 조회합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-admin-deleted-users" to ApiMeta(
            tag = "Admin",
            summary = "관리자 소프트 탈퇴 사용자 목록 조회",
            description = "소프트 탈퇴된 사용자 목록을 페이징 조회합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-admin-user-status-audits" to ApiMeta(
            tag = "Admin",
            summary = "관리자 사용자 상태 변경 이력 조회",
            description = "특정 사용자에 대한 차단/해제/탈퇴 상태 변경 이력을 조회합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-local-signup" to ApiMeta(
            tag = "Auth",
            summary = "로컬 회원가입",
            description = "이메일/비밀번호 기반 로컬 계정을 생성합니다.",
            authMarkdown = "- 인증 불필요",
        ),
        "auth-local-login" to ApiMeta(
            tag = "Auth",
            summary = "로컬 로그인",
            description = "이메일/비밀번호로 로그인하고 토큰을 발급합니다.",
            authMarkdown = "- 인증 불필요",
        ),
        "auth-local-refresh" to ApiMeta(
            tag = "Auth",
            summary = "토큰 재발급(Refresh)",
            description = "Refresh Token으로 Access Token을 재발급합니다.",
            authMarkdown = "- 인증 불필요(일반적으로 refreshToken 자체가 인증 수단)",
        ),
        "auth-local-logout" to ApiMeta(
            tag = "Auth",
            summary = "로그아웃",
            description = "로그아웃을 수행합니다(단건/전체 옵션).",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요",
        ),
        "auth-local-withdraw" to ApiMeta(
            tag = "Auth",
            summary = "회원 탈퇴(소프트 삭제)",
            description = "회원 탈퇴를 소프트 삭제로 처리하고 보관 만료일을 기록합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요",
        ),
        "auth-oauth2-authorize-url" to ApiMeta(
            tag = "OAuth2",
            summary = "OAuth2 인가 URL 조회",
            description = "OAuth2 인가 URL 경로를 반환합니다.",
            authMarkdown = "- 인증 불필요",
        ),
        "health" to ApiMeta(
            tag = "Health",
            summary = "헬스 체크",
            description = "서비스 상태 확인용 엔드포인트입니다.",
            authMarkdown = "- 인증 불필요",
        ),
    )

    fun resolve(id: String): ApiMeta =
        map[id] ?: ApiMeta(tag = "API", summary = id, description = id, authMarkdown = "- 인증 정책 확인 필요")
}

private data class ApiMeta(
    val tag: String,
    val summary: String,
    val description: String,
    val authMarkdown: String,
)
