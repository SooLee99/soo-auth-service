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
    // RestDocs 식별자별 카테고리/요약/설명
    private val map = mapOf(
        "health" to ApiMeta(
            tag = "Health",
            summary = "공개 헬스 체크",
            description = "인증 없이 서비스 기본 상태를 확인합니다.",
            authMarkdown = "- 인증 불필요",
        ),
        "auth-admin-health" to ApiMeta(
            tag = "Health",
            summary = "관리자 상세 헬스 체크",
            description = "관리자 권한으로 DB/Redis/System/링크 상세 상태를 조회합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-email-login" to ApiMeta(
            tag = "Auth",
            summary = "이메일 로그인",
            description = "이메일/비밀번호로 로그인하고 액세스/리프레시 토큰을 발급합니다.",
            authMarkdown = "- 인증 불필요",
        ),
        "auth-email-signup" to ApiMeta(
            tag = "Auth",
            summary = "이메일 회원가입",
            description = "이메일/비밀번호 기반 로컬 계정을 생성합니다. 휴대폰 인증은 선택입니다.",
            authMarkdown = "- 인증 불필요",
        ),
        "auth-phone-signup" to ApiMeta(
            tag = "Auth",
            summary = "휴대폰 회원가입",
            description = "휴대폰 인증 토큰으로 계정을 생성합니다.",
            authMarkdown = "- 인증 불필요",
        ),
        "auth-phone-login" to ApiMeta(
            tag = "Auth",
            summary = "휴대폰 로그인",
            description = "휴대폰 인증 토큰으로 로그인하고 액세스/리프레시 토큰을 발급합니다.",
            authMarkdown = "- 인증 불필요",
        ),
        "auth-id-signup" to ApiMeta(
            tag = "Auth",
            summary = "아이디 회원가입",
            description = "아이디/비밀번호 계정을 생성합니다. 휴대폰 인증이 필수입니다.",
            authMarkdown = "- 인증 불필요",
        ),
        "auth-id-login" to ApiMeta(
            tag = "Auth",
            summary = "아이디 로그인",
            description = "아이디/비밀번호로 로그인하고 액세스/리프레시 토큰을 발급합니다.",
            authMarkdown = "- 인증 불필요",
        ),
        "auth-token-refresh" to ApiMeta(
            tag = "Auth",
            summary = "토큰 재발급",
            description = "리프레시 토큰으로 새 액세스/리프레시 토큰을 발급합니다.",
            authMarkdown = "- 인증 불필요(리프레시 토큰 필요)",
        ),
        "auth-local-login" to ApiMeta(
            tag = "Local Auth",
            summary = "로컬 로그인",
            description = "이메일/비밀번호로 로그인하고 액세스/리프레시 토큰을 발급합니다.",
            authMarkdown = "- 인증 불필요",
        ),
        "auth-local-login-phone" to ApiMeta(
            tag = "Local Auth",
            summary = "휴대폰 로그인",
            description = "휴대폰 인증 토큰으로 로그인하고 액세스/리프레시 토큰을 발급합니다.",
            authMarkdown = "- 인증 불필요",
        ),
        "auth-local-signup" to ApiMeta(
            tag = "Local Auth",
            summary = "로컬 회원가입",
            description = "이메일/비밀번호 기반 로컬 계정을 생성합니다.",
            authMarkdown = "- 인증 불필요",
        ),
        "auth-local-signup-phone" to ApiMeta(
            tag = "Local Auth",
            summary = "전화번호 간편 회원가입",
            description = "전화번호 인증 토큰으로 최소 정보 계정을 생성합니다.",
            authMarkdown = "- 인증 불필요",
        ),
        "auth-local-refresh" to ApiMeta(
            tag = "Local Auth",
            summary = "토큰 재발급",
            description = "리프레시 토큰으로 새 액세스/리프레시 토큰을 발급합니다.",
            authMarkdown = "- 인증 불필요(리프레시 토큰 필요)",
        ),
        "auth-local-logout" to ApiMeta(
            tag = "Local Auth",
            summary = "로그아웃",
            description = "현재 디바이스 또는 전체 디바이스 로그아웃을 처리합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요",
        ),
        "auth-local-withdraw" to ApiMeta(
            tag = "Local Auth",
            summary = "회원 탈퇴",
            description = "사용자를 소프트 삭제하고 인증정보/토큰을 정리합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요",
        ),
        "auth-local-phone-verification-request" to ApiMeta(
            tag = "Local Phone",
            summary = "휴대폰 인증번호 발급",
            description = "휴대폰 번호로 인증번호를 발급하고 verificationId를 반환합니다.",
            authMarkdown = "- 인증 불필요",
        ),
        "auth-local-phone-verification-confirm" to ApiMeta(
            tag = "Local Phone",
            summary = "휴대폰 인증번호 확인",
            description = "인증번호를 검증하고 회원가입에 사용할 phoneVerificationToken을 발급합니다.",
            authMarkdown = "- 인증 불필요",
        ),
        "auth-oauth2-authorize-url" to ApiMeta(
            tag = "OAuth2",
            summary = "OAuth2 인가 URL 조회",
            description = "소셜 로그인 시작 경로(인가 URL)를 반환합니다.",
            authMarkdown = "- 인증 불필요",
        ),
        "auth-admin-login-history" to ApiMeta(
            tag = "Admin Audit",
            summary = "관리자 로그인 이력 조회",
            description = "관리자 권한으로 로그인 이력을 페이징 조회합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-admin-users-list" to ApiMeta(
            tag = "Admin Users",
            summary = "관리자 사용자 목록 조회",
            description = "검색/필터 조건으로 사용자 목록을 페이징 조회합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-admin-user-detail" to ApiMeta(
            tag = "Admin Users",
            summary = "관리자 사용자 상세 조회",
            description = "특정 사용자의 상세 정보를 조회합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-admin-user-update" to ApiMeta(
            tag = "Admin Users",
            summary = "관리자 사용자 정보 수정",
            description = "특정 사용자의 프로필/권한/상태를 수정합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-admin-user-password-reset" to ApiMeta(
            tag = "Admin Users",
            summary = "관리자 사용자 비밀번호 재설정",
            description = "비밀번호를 재설정하고 기존 토큰을 무효화합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-admin-user-tokens-revoke" to ApiMeta(
            tag = "Admin Users",
            summary = "관리자 사용자 토큰 강제 만료",
            description = "특정 사용자의 모든 토큰을 즉시 무효화합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-admin-user-delete" to ApiMeta(
            tag = "Admin Users",
            summary = "관리자 사용자 소프트 삭제",
            description = "특정 사용자를 소프트 삭제 처리합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-admin-user-block" to ApiMeta(
            tag = "Admin User Status",
            summary = "관리자 사용자 차단",
            description = "관리자 권한으로 특정 사용자를 차단합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-admin-user-unblock" to ApiMeta(
            tag = "Admin User Status",
            summary = "관리자 사용자 차단 해제",
            description = "관리자 권한으로 특정 사용자의 차단을 해제합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-admin-blocked-users" to ApiMeta(
            tag = "Admin User Status",
            summary = "관리자 차단 사용자 목록 조회",
            description = "차단된 사용자 목록을 페이징 조회합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-admin-deleted-users" to ApiMeta(
            tag = "Admin User Status",
            summary = "관리자 소프트 탈퇴 사용자 목록 조회",
            description = "소프트 탈퇴된 사용자 목록을 페이징 조회합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-admin-user-status-audits" to ApiMeta(
            tag = "Admin User Status",
            summary = "관리자 사용자 상태 변경 이력 조회",
            description = "특정 사용자에 대한 차단/해제/탈퇴 상태 변경 이력을 조회합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-admin-sms-send" to ApiMeta(
            tag = "Admin SMS",
            summary = "관리자 SMS 발송",
            description = "관리자 권한으로 단문 메시지를 발송합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-admin-sms-logs" to ApiMeta(
            tag = "Admin SMS",
            summary = "관리자 SMS 발송 이력 조회",
            description = "발송 이력을 기간/페이지 조건으로 조회합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
        ),
        "auth-admin-sms-stats" to ApiMeta(
            tag = "Admin SMS",
            summary = "관리자 SMS 통계 조회",
            description = "발송 성공/실패 통계를 조회합니다.",
            authMarkdown = "- `Authorization: Bearer {accessToken}` 필요\n- 권한: `ROLE_ADMIN` 필요",
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
