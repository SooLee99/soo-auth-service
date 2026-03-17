package io.soo.springboot.core.api.restdocs

import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.core.api.controller.v1.AdminAuthController
import io.soo.springboot.core.api.controller.v1.request.AdminUserBlockRequest
import io.soo.springboot.core.api.controller.v1.request.AdminUserDeleteRequest
import io.soo.springboot.core.api.controller.v1.request.AdminUserPasswordResetRequest
import io.soo.springboot.core.api.controller.v1.request.AdminUserUpdateRequest
import io.soo.springboot.core.api.security.auth.UserIdResolver
import io.soo.springboot.core.domain.AdminUserManagementService
import io.soo.springboot.core.domain.AdminUserBlockService
import io.soo.springboot.core.domain.LoginHistoryService
import io.soo.springboot.core.enums.AdminUserActionType
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Gender
import io.soo.springboot.core.enums.Role
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.storage.db.core.LoginHistory
import io.soo.springboot.storage.db.core.LoginHistoryEntity
import io.soo.springboot.storage.db.core.User
import io.soo.springboot.storage.db.core.UserStatusAuditLog
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
import org.springframework.security.authentication.TestingAuthenticationToken
import java.time.Instant
import java.time.LocalDateTime

class AdminAuthControllerDocsTest : RestDocsTest() {

    private val loginHistoryService = mockk<LoginHistoryService>()
    private val userIdResolver = mockk<UserIdResolver>()
    private val adminUserBlockService = mockk<AdminUserBlockService>()
    private val adminUserManagementService = mockk<AdminUserManagementService>(relaxed = true)
    private lateinit var controller: AdminAuthController

    @BeforeEach
    fun init() {
        controller = AdminAuthController(loginHistoryService, userIdResolver, adminUserBlockService, adminUserManagementService)
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

    @Test
    fun blockUser() {
        every { userIdResolver.resolve(any()) } returns 100L
        every { adminUserBlockService.blockUser(1L, 100L, "abuse") } returns sampleBlockedUser()

        val authentication = TestingAuthenticationToken("admin", "password", "ROLE_ADMIN")

        given()
            .auth().principal(authentication)
            .contentType("application/json")
            .header("Authorization", "Bearer token")
            .body(AdminUserBlockRequest(reason = "abuse"))
            .`when`()
            .post("/api/v1/auth/admin/users/{userId}/block", 1)
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-user-block",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("Authorization").description("Bearer 액세스 토큰"),
                    ),
                    pathParameters(
                        parameterWithName("userId").description("차단 대상 사용자 ID"),
                    ),
                    requestFields(
                        fieldWithPath("reason").type(JsonFieldType.STRING).optional().description("차단 사유"),
                    ),
                    relaxedResponseFields(*adminUserStateResponseDescriptors().toTypedArray()),
                )
            )
    }

    @Test
    fun unblockUser() {
        every { userIdResolver.resolve(any()) } returns 100L
        every { adminUserBlockService.unblockUser(1L, 100L) } returns sampleBlockedUser().copy(
            userStatus = UserStatus.ACTIVE,
            blocked = false,
            unblockedAt = Instant.parse("2026-03-16T12:00:00Z"),
            unblockedByAdminId = 100L,
        )

        val authentication = TestingAuthenticationToken("admin", "password", "ROLE_ADMIN")

        given()
            .auth().principal(authentication)
            .header("Authorization", "Bearer token")
            .`when`()
            .post("/api/v1/auth/admin/users/{userId}/unblock", 1)
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-user-unblock",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("Authorization").description("Bearer 액세스 토큰"),
                    ),
                    pathParameters(
                        parameterWithName("userId").description("차단 해제 대상 사용자 ID"),
                    ),
                    relaxedResponseFields(*adminUserStateResponseDescriptors().toTypedArray()),
                )
            )
    }

    @Test
    fun userDetail() {
        every { adminUserManagementService.getUserDetail(1L) } returns sampleAdminManagedUser()

        val authentication = TestingAuthenticationToken("admin", "password", "ROLE_ADMIN")

        given()
            .auth().principal(authentication)
            .header("Authorization", "Bearer token")
            .`when`()
            .get("/api/v1/auth/admin/users/{userId}", 1)
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-user-detail",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("Authorization").description("Bearer 액세스 토큰"),
                    ),
                    pathParameters(
                        parameterWithName("userId").description("조회 대상 사용자 ID"),
                    ),
                    relaxedResponseFields(*adminUserDetailResponseDescriptors().toTypedArray()),
                )
            )
    }

    @Test
    fun updateUser() {
        every { userIdResolver.resolve(any()) } returns 100L
        every { adminUserManagementService.updateUser(1L, any(), 100L) } returns sampleAdminManagedUser().copy(
            nickname = "updated-nickname",
            role = Role.ADMIN,
        )

        val authentication = TestingAuthenticationToken("admin", "password", "ROLE_ADMIN")

        given()
            .auth().principal(authentication)
            .contentType("application/json")
            .header("Authorization", "Bearer token")
            .body(
                AdminUserUpdateRequest(
                    nickname = "updated-nickname",
                    role = Role.ADMIN,
                    blocked = false,
                )
            )
            .`when`()
            .patch("/api/v1/auth/admin/users/{userId}", 1)
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-user-update",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("Authorization").description("Bearer 액세스 토큰"),
                    ),
                    pathParameters(
                        parameterWithName("userId").description("수정 대상 사용자 ID"),
                    ),
                    requestFields(
                        fieldWithPath("email").type(JsonFieldType.STRING).optional().description("이메일"),
                        fieldWithPath("phoneNumber").type(JsonFieldType.STRING).optional().description("휴대폰 번호"),
                        fieldWithPath("name").type(JsonFieldType.STRING).optional().description("이름"),
                        fieldWithPath("nickname").type(JsonFieldType.STRING).optional().description("닉네임"),
                        fieldWithPath("gender").type(JsonFieldType.STRING).optional().description("성별"),
                        fieldWithPath("locale").type(JsonFieldType.STRING).optional().description("로케일"),
                        fieldWithPath("birthyear").type(JsonFieldType.STRING).optional().description("출생연도"),
                        fieldWithPath("birthday").type(JsonFieldType.STRING).optional().description("생일"),
                        fieldWithPath("profileImageUrl").type(JsonFieldType.STRING).optional().description("프로필 이미지 URL"),
                        fieldWithPath("thumbnailImageUrl").type(JsonFieldType.STRING).optional().description("썸네일 이미지 URL"),
                        fieldWithPath("role").type(JsonFieldType.STRING).optional().description("권한(USER/ADMIN)"),
                        fieldWithPath("userStatus").type(JsonFieldType.STRING).optional().description("상태(ACTIVE/BLOCKED)"),
                        fieldWithPath("blocked").type(JsonFieldType.BOOLEAN).optional().description("차단 여부"),
                        fieldWithPath("blockedReason").type(JsonFieldType.STRING).optional().description("차단 사유"),
                        fieldWithPath("emailVerified").type(JsonFieldType.BOOLEAN).optional().description("이메일 인증 여부"),
                        fieldWithPath("phoneVerified").type(JsonFieldType.BOOLEAN).optional().description("휴대폰 인증 여부"),
                    ),
                    relaxedResponseFields(*adminUserDetailResponseDescriptors().toTypedArray()),
                )
            )
    }

    @Test
    fun deleteUser() {
        every { userIdResolver.resolve(any()) } returns 100L
        every { adminUserManagementService.deleteUser(1L, "admin-delete", 100L) } returns sampleSoftDeletedUser()

        val authentication = TestingAuthenticationToken("admin", "password", "ROLE_ADMIN")

        given()
            .auth().principal(authentication)
            .contentType("application/json")
            .header("Authorization", "Bearer token")
            .body(AdminUserDeleteRequest(reason = "admin-delete"))
            .`when`()
            .post("/api/v1/auth/admin/users/{userId}/delete", 1)
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-user-delete",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("Authorization").description("Bearer 액세스 토큰"),
                    ),
                    pathParameters(
                        parameterWithName("userId").description("삭제 대상 사용자 ID"),
                    ),
                    requestFields(
                        fieldWithPath("reason").type(JsonFieldType.STRING).optional().description("삭제 사유"),
                    ),
                    relaxedResponseFields(*adminUserStateResponseDescriptors().toTypedArray()),
                )
            )
    }

    @Test
    fun listUsers() {
        val page = PageImpl(listOf(sampleAdminManagedUser()), PageRequest.of(0, 20), 1)
        every { adminUserManagementService.listUsers(any(), any(), any(), any(), any()) } returns page

        val authentication = TestingAuthenticationToken("admin", "password", "ROLE_ADMIN")

        given()
            .auth().principal(authentication)
            .header("Authorization", "Bearer token")
            .queryParam("page", "0")
            .queryParam("size", "20")
            .queryParam("keyword", "user")
            .queryParam("userStatus", "ACTIVE")
            .queryParam("role", "USER")
            .queryParam("authProvider", "LOCAL")
            .`when`()
            .get("/api/v1/auth/admin/users")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-users-list",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("Authorization").description("Bearer 액세스 토큰"),
                    ),
                    queryParameters(
                        parameterWithName("page").optional().description("페이지 번호 (0부터 시작)"),
                        parameterWithName("size").optional().description("페이지 크기"),
                        parameterWithName("keyword").optional().description("검색어(email/name/nickname/phone)"),
                        parameterWithName("userStatus").optional().description("상태 필터(ACTIVE/BLOCKED/SOFT_DELETED)"),
                        parameterWithName("role").optional().description("권한 필터(USER/ADMIN)"),
                        parameterWithName("authProvider").optional().description("인증 제공자 필터(LOCAL/GOOGLE/NAVER/KAKAO)"),
                    ),
                    relaxedResponseFields(*adminUserListResponseDescriptors().toTypedArray()),
                )
            )
    }

    @Test
    fun resetUserPassword() {
        every { adminUserManagementService.resetPassword(1L, any()) } returns sampleAdminManagedUser()

        val authentication = TestingAuthenticationToken("admin", "password", "ROLE_ADMIN")

        given()
            .auth().principal(authentication)
            .contentType("application/json")
            .header("Authorization", "Bearer token")
            .body(AdminUserPasswordResetRequest(newPassword = "NewP@ssword1!"))
            .`when`()
            .post("/api/v1/auth/admin/users/{userId}/password/reset", 1)
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-user-password-reset",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("Authorization").description("Bearer 액세스 토큰"),
                    ),
                    pathParameters(
                        parameterWithName("userId").description("비밀번호 재설정 대상 사용자 ID"),
                    ),
                    requestFields(
                        fieldWithPath("newPassword").type(JsonFieldType.STRING).description("새 비밀번호"),
                    ),
                    relaxedResponseFields(*adminUserDetailResponseDescriptors().toTypedArray()),
                )
            )
    }

    @Test
    fun revokeUserTokens() {
        every { adminUserManagementService.revokeUserTokens(1L) } returns sampleAdminManagedUser()

        val authentication = TestingAuthenticationToken("admin", "password", "ROLE_ADMIN")

        given()
            .auth().principal(authentication)
            .header("Authorization", "Bearer token")
            .`when`()
            .post("/api/v1/auth/admin/users/{userId}/tokens/revoke", 1)
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-user-tokens-revoke",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("Authorization").description("Bearer 액세스 토큰"),
                    ),
                    pathParameters(
                        parameterWithName("userId").description("토큰 강제 만료 대상 사용자 ID"),
                    ),
                    relaxedResponseFields(*adminUserDetailResponseDescriptors().toTypedArray()),
                )
            )
    }

    @Test
    fun blockedUsers() {
        val page = PageImpl(listOf(sampleBlockedUser()), PageRequest.of(0, 20), 1)
        every { adminUserBlockService.findBlockedUsers(any()) } returns page

        val authentication = TestingAuthenticationToken("admin", "password", "ROLE_ADMIN")

        given()
            .auth().principal(authentication)
            .header("Authorization", "Bearer token")
            .queryParam("page", "0")
            .queryParam("size", "20")
            .`when`()
            .get("/api/v1/auth/admin/users/blocked")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-blocked-users",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("Authorization").description("Bearer 액세스 토큰"),
                    ),
                    queryParameters(
                        parameterWithName("page").optional().description("페이지 번호 (0부터 시작)"),
                        parameterWithName("size").optional().description("페이지 크기"),
                    ),
                    relaxedResponseFields(*adminUserPageResponseDescriptors().toTypedArray()),
                )
            )
    }

    @Test
    fun deletedUsers() {
        val page = PageImpl(listOf(sampleSoftDeletedUser()), PageRequest.of(0, 20), 1)
        every { adminUserBlockService.findSoftDeletedUsers(any()) } returns page

        val authentication = TestingAuthenticationToken("admin", "password", "ROLE_ADMIN")

        given()
            .auth().principal(authentication)
            .header("Authorization", "Bearer token")
            .queryParam("page", "0")
            .queryParam("size", "20")
            .`when`()
            .get("/api/v1/auth/admin/users/deleted")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-deleted-users",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("Authorization").description("Bearer 액세스 토큰"),
                    ),
                    queryParameters(
                        parameterWithName("page").optional().description("페이지 번호 (0부터 시작)"),
                        parameterWithName("size").optional().description("페이지 크기"),
                    ),
                    relaxedResponseFields(*adminUserPageResponseDescriptors().toTypedArray()),
                )
            )
    }

    @Test
    fun statusAudits() {
        val audit = UserStatusAuditLog(
            id = 1L,
            targetUserId = 1L,
            actorUserId = 100L,
            actionType = AdminUserActionType.BLOCK,
            reason = "abuse",
            actionAt = Instant.parse("2026-03-16T10:00:00Z"),
        )
        val page = PageImpl(listOf(audit), PageRequest.of(0, 20), 1)
        every { adminUserBlockService.findStatusAuditLogs(1L, any()) } returns page

        val authentication = TestingAuthenticationToken("admin", "password", "ROLE_ADMIN")

        val responseDescriptors = listOf(
            fieldWithPath("result").type(JsonFieldType.STRING).description("결과"),
            fieldWithPath("meta").type(JsonFieldType.OBJECT).description("메타 정보"),
            fieldWithPath("meta.timestamp").type(JsonFieldType.STRING).description("응답 생성 시각"),
            fieldWithPath("meta.request").type(JsonFieldType.OBJECT).description("요청 정보"),
            fieldWithPath("meta.request.path").type(JsonFieldType.STRING).description("요청 경로"),
            fieldWithPath("meta.request.method").type(JsonFieldType.STRING).description("HTTP 메서드"),
            fieldWithPath("meta.request.query").type(JsonFieldType.VARIES).optional().description("쿼리 스트링(있으면 문자열, 없으면 null)"),
            fieldWithPath("data").type(JsonFieldType.OBJECT).description("상태 이력 페이지(Page)"),
            fieldWithPath("data.content").type(JsonFieldType.ARRAY).description("상태 이력 목록"),
            fieldWithPath("data.content[].id").type(JsonFieldType.NUMBER).description("이력 ID"),
            fieldWithPath("data.content[].targetUserId").type(JsonFieldType.NUMBER).description("대상 사용자 ID"),
            fieldWithPath("data.content[].actorUserId").type(JsonFieldType.NUMBER).description("처리 관리자/사용자 ID"),
            fieldWithPath("data.content[].actionType").type(JsonFieldType.STRING).description("상태 변경 액션(BLOCK/UNBLOCK/SOFT_DELETE)"),
            fieldWithPath("data.content[].reason").type(JsonFieldType.VARIES).optional().description("사유"),
            fieldWithPath("data.content[].actionAt").type(JsonFieldType.STRING).description("처리 시각"),
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
            fieldWithPath("error").type(JsonFieldType.VARIES).optional().description("에러 정보"),
        )

        given()
            .auth().principal(authentication)
            .header("Authorization", "Bearer token")
            .queryParam("page", "0")
            .queryParam("size", "20")
            .`when`()
            .get("/api/v1/auth/admin/users/{userId}/status-audits", 1)
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-user-status-audits",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("Authorization").description("Bearer 액세스 토큰"),
                    ),
                    pathParameters(
                        parameterWithName("userId").description("이력 조회 대상 사용자 ID"),
                    ),
                    queryParameters(
                        parameterWithName("page").optional().description("페이지 번호 (0부터 시작)"),
                        parameterWithName("size").optional().description("페이지 크기"),
                    ),
                    relaxedResponseFields(*responseDescriptors.toTypedArray()),
                )
            )
    }

    private fun adminUserStateResponseDescriptors() = listOf(
        fieldWithPath("result").type(JsonFieldType.STRING).description("결과"),
        fieldWithPath("meta").type(JsonFieldType.OBJECT).description("메타 정보"),
        fieldWithPath("meta.timestamp").type(JsonFieldType.STRING).description("응답 생성 시각"),
        fieldWithPath("meta.request").type(JsonFieldType.OBJECT).description("요청 정보"),
        fieldWithPath("meta.request.path").type(JsonFieldType.STRING).description("요청 경로"),
        fieldWithPath("meta.request.method").type(JsonFieldType.STRING).description("HTTP 메서드"),
        fieldWithPath("meta.request.query").type(JsonFieldType.VARIES).optional().description("쿼리 스트링"),
        fieldWithPath("data").type(JsonFieldType.OBJECT).description("사용자 상태 정보"),
        fieldWithPath("data.userId").type(JsonFieldType.NUMBER).description("사용자 식별자"),
        fieldWithPath("data.userStatus").type(JsonFieldType.STRING).description("사용자 상태(ACTIVE/BLOCKED/SOFT_DELETED)"),
        fieldWithPath("data.blocked").type(JsonFieldType.BOOLEAN).description("차단 여부"),
        fieldWithPath("data.blockedReason").type(JsonFieldType.VARIES).optional().description("차단 사유"),
        fieldWithPath("data.blockedAt").type(JsonFieldType.VARIES).optional().description("차단 시각"),
        fieldWithPath("data.blockedByAdminId").type(JsonFieldType.VARIES).optional().description("차단한 관리자 ID"),
        fieldWithPath("data.unblockedAt").type(JsonFieldType.VARIES).optional().description("차단 해제 시각"),
        fieldWithPath("data.unblockedByAdminId").type(JsonFieldType.VARIES).optional().description("차단 해제 관리자 ID"),
        fieldWithPath("data.deletedAt").type(JsonFieldType.VARIES).optional().description("탈퇴 시각"),
        fieldWithPath("data.deletionReason").type(JsonFieldType.VARIES).optional().description("탈퇴 사유"),
        fieldWithPath("data.retentionUntil").type(JsonFieldType.VARIES).optional().description("보관 만료 예정 시각"),
        fieldWithPath("error").type(JsonFieldType.VARIES).optional().description("에러 정보"),
    )

    private fun adminUserPageResponseDescriptors() = listOf(
        fieldWithPath("result").type(JsonFieldType.STRING).description("결과"),
        fieldWithPath("meta").type(JsonFieldType.OBJECT).description("메타 정보"),
        fieldWithPath("meta.timestamp").type(JsonFieldType.STRING).description("응답 생성 시각"),
        fieldWithPath("meta.request").type(JsonFieldType.OBJECT).description("요청 정보"),
        fieldWithPath("meta.request.path").type(JsonFieldType.STRING).description("요청 경로"),
        fieldWithPath("meta.request.method").type(JsonFieldType.STRING).description("HTTP 메서드"),
        fieldWithPath("meta.request.query").type(JsonFieldType.VARIES).optional().description("쿼리 스트링"),
        fieldWithPath("data").type(JsonFieldType.OBJECT).description("사용자 상태 목록 페이지"),
        fieldWithPath("data.content").type(JsonFieldType.ARRAY).description("사용자 목록"),
        fieldWithPath("data.content[].userId").type(JsonFieldType.NUMBER).description("사용자 식별자"),
        fieldWithPath("data.content[].userStatus").type(JsonFieldType.STRING).description("사용자 상태"),
        fieldWithPath("data.content[].blocked").type(JsonFieldType.BOOLEAN).description("차단 여부"),
        fieldWithPath("data.content[].blockedReason").type(JsonFieldType.VARIES).optional().description("차단 사유"),
        fieldWithPath("data.content[].blockedAt").type(JsonFieldType.VARIES).optional().description("차단 시각"),
        fieldWithPath("data.content[].blockedByAdminId").type(JsonFieldType.VARIES).optional().description("차단 관리자"),
        fieldWithPath("data.content[].unblockedAt").type(JsonFieldType.VARIES).optional().description("차단 해제 시각"),
        fieldWithPath("data.content[].unblockedByAdminId").type(JsonFieldType.VARIES).optional().description("차단 해제 관리자"),
        fieldWithPath("data.content[].deletedAt").type(JsonFieldType.VARIES).optional().description("탈퇴 시각"),
        fieldWithPath("data.content[].deletionReason").type(JsonFieldType.VARIES).optional().description("탈퇴 사유"),
        fieldWithPath("data.content[].retentionUntil").type(JsonFieldType.VARIES).optional().description("보관 만료 예정 시각"),
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
        fieldWithPath("error").type(JsonFieldType.VARIES).optional().description("에러 정보"),
    )

    private fun adminUserDetailResponseDescriptors() = listOf(
        fieldWithPath("result").type(JsonFieldType.STRING).description("결과"),
        fieldWithPath("meta").type(JsonFieldType.OBJECT).description("메타 정보"),
        fieldWithPath("meta.timestamp").type(JsonFieldType.STRING).description("응답 생성 시각"),
        fieldWithPath("meta.request").type(JsonFieldType.OBJECT).description("요청 정보"),
        fieldWithPath("meta.request.path").type(JsonFieldType.STRING).description("요청 경로"),
        fieldWithPath("meta.request.method").type(JsonFieldType.STRING).description("HTTP 메서드"),
        fieldWithPath("meta.request.query").type(JsonFieldType.VARIES).optional().description("쿼리 스트링"),
        fieldWithPath("data").type(JsonFieldType.OBJECT).description("사용자 상세 정보"),
        fieldWithPath("data.userId").type(JsonFieldType.NUMBER).description("사용자 ID"),
        fieldWithPath("data.email").type(JsonFieldType.STRING).description("이메일"),
        fieldWithPath("data.emailVerified").type(JsonFieldType.BOOLEAN).description("이메일 인증 여부"),
        fieldWithPath("data.phoneNumber").type(JsonFieldType.VARIES).optional().description("전화번호"),
        fieldWithPath("data.phoneVerified").type(JsonFieldType.BOOLEAN).description("전화번호 인증 여부"),
        fieldWithPath("data.name").type(JsonFieldType.VARIES).optional().description("이름"),
        fieldWithPath("data.nickname").type(JsonFieldType.VARIES).optional().description("닉네임"),
        fieldWithPath("data.gender").type(JsonFieldType.STRING).description("성별"),
        fieldWithPath("data.locale").type(JsonFieldType.VARIES).optional().description("로케일"),
        fieldWithPath("data.birthyear").type(JsonFieldType.VARIES).optional().description("출생연도"),
        fieldWithPath("data.birthday").type(JsonFieldType.VARIES).optional().description("생일"),
        fieldWithPath("data.profileImageUrl").type(JsonFieldType.VARIES).optional().description("프로필 이미지 URL"),
        fieldWithPath("data.thumbnailImageUrl").type(JsonFieldType.VARIES).optional().description("썸네일 이미지 URL"),
        fieldWithPath("data.authProvider").type(JsonFieldType.STRING).description("인증 제공자"),
        fieldWithPath("data.oauthProviderUserId").type(JsonFieldType.VARIES).optional().description("소셜 사용자 ID"),
        fieldWithPath("data.role").type(JsonFieldType.STRING).description("권한"),
        fieldWithPath("data.userStatus").type(JsonFieldType.STRING).description("상태"),
        fieldWithPath("data.blocked").type(JsonFieldType.BOOLEAN).description("차단 여부"),
        fieldWithPath("data.blockedReason").type(JsonFieldType.VARIES).optional().description("차단 사유"),
        fieldWithPath("data.blockedAt").type(JsonFieldType.VARIES).optional().description("차단 시각"),
        fieldWithPath("data.blockedByAdminId").type(JsonFieldType.VARIES).optional().description("차단 관리자 ID"),
        fieldWithPath("data.unblockedAt").type(JsonFieldType.VARIES).optional().description("차단 해제 시각"),
        fieldWithPath("data.unblockedByAdminId").type(JsonFieldType.VARIES).optional().description("차단 해제 관리자 ID"),
        fieldWithPath("data.deletedAt").type(JsonFieldType.VARIES).optional().description("삭제 시각"),
        fieldWithPath("data.deletionReason").type(JsonFieldType.VARIES).optional().description("삭제 사유"),
        fieldWithPath("data.retentionUntil").type(JsonFieldType.VARIES).optional().description("보관 만료 시각"),
        fieldWithPath("error").type(JsonFieldType.VARIES).optional().description("에러 정보"),
    )

    private fun adminUserListResponseDescriptors() = listOf(
        fieldWithPath("result").type(JsonFieldType.STRING).description("결과"),
        fieldWithPath("meta").type(JsonFieldType.OBJECT).description("메타 정보"),
        fieldWithPath("meta.timestamp").type(JsonFieldType.STRING).description("응답 생성 시각"),
        fieldWithPath("meta.request").type(JsonFieldType.OBJECT).description("요청 정보"),
        fieldWithPath("meta.request.path").type(JsonFieldType.STRING).description("요청 경로"),
        fieldWithPath("meta.request.method").type(JsonFieldType.STRING).description("HTTP 메서드"),
        fieldWithPath("meta.request.query").type(JsonFieldType.VARIES).optional().description("쿼리 스트링"),
        fieldWithPath("data").type(JsonFieldType.OBJECT).description("사용자 목록 페이지"),
        fieldWithPath("data.content").type(JsonFieldType.ARRAY).description("사용자 요약 목록"),
        fieldWithPath("data.content[].userId").type(JsonFieldType.NUMBER).description("사용자 ID"),
        fieldWithPath("data.content[].email").type(JsonFieldType.STRING).description("이메일"),
        fieldWithPath("data.content[].name").type(JsonFieldType.VARIES).optional().description("이름"),
        fieldWithPath("data.content[].nickname").type(JsonFieldType.VARIES).optional().description("닉네임"),
        fieldWithPath("data.content[].role").type(JsonFieldType.STRING).description("권한"),
        fieldWithPath("data.content[].authProvider").type(JsonFieldType.STRING).description("인증 제공자"),
        fieldWithPath("data.content[].userStatus").type(JsonFieldType.STRING).description("상태"),
        fieldWithPath("data.content[].blocked").type(JsonFieldType.BOOLEAN).description("차단 여부"),
        fieldWithPath("data.content[].deletedAt").type(JsonFieldType.VARIES).optional().description("삭제 시각"),
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
        fieldWithPath("error").type(JsonFieldType.VARIES).optional().description("에러 정보"),
    )

    private fun sampleBlockedUser() = User(
        id = 1L,
        email = "user@example.com",
        phoneNumber = null,
        name = null,
        nickname = null,
        authProvider = AuthProvider.LOCAL,
        userStatus = UserStatus.BLOCKED,
        blocked = true,
        blockedReason = "abuse",
        blockedAt = Instant.parse("2026-03-16T10:00:00Z"),
        blockedByAdminId = 100L,
    )

    private fun sampleSoftDeletedUser() = User(
        id = 2L,
        email = "withdrawn@example.com",
        phoneNumber = null,
        name = null,
        nickname = null,
        authProvider = AuthProvider.LOCAL,
        userStatus = UserStatus.SOFT_DELETED,
        blocked = false,
        deletedAt = Instant.parse("2026-03-16T11:00:00Z"),
        deletionReason = "privacy",
        retentionUntil = Instant.parse("2031-03-16T11:00:00Z"),
    )

    private fun sampleAdminManagedUser() = User(
        id = 1L,
        email = "user@example.com",
        emailVerified = true,
        phoneNumber = "+82 10-1234-5678",
        phoneVerified = true,
        name = "홍길동",
        nickname = "gildong",
        gender = Gender.MALE,
        locale = "ko-KR",
        birthyear = "1990",
        birthday = "01-31",
        profileImageUrl = "https://cdn.example.com/profile.png",
        thumbnailImageUrl = "https://cdn.example.com/thumb.png",
        authProvider = AuthProvider.LOCAL,
        role = Role.USER,
        userStatus = UserStatus.ACTIVE,
        blocked = false,
    )
}
