package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.AdminUserBlockRequest
import io.soo.springboot.core.api.controller.v1.request.AdminUserDeleteRequest
import io.soo.springboot.core.api.controller.v1.request.AdminUserPasswordResetRequest
import io.soo.springboot.core.api.controller.v1.request.AdminUserUpdateRequest
import io.soo.springboot.core.api.controller.v1.response.AdminUserDetailResponse
import io.soo.springboot.core.api.controller.v1.response.AdminUserBlockResponse
import io.soo.springboot.core.api.controller.v1.response.AdminUserSummaryResponse
import io.soo.springboot.core.api.controller.v1.response.UserStatusAuditLogResponse
import io.soo.springboot.core.api.security.auth.UserIdResolver
import io.soo.springboot.core.domain.admin.UserUpdateCmd
import io.soo.springboot.core.domain.admin.UserAdmin
import io.soo.springboot.core.domain.admin.UserBlock
import io.soo.springboot.core.domain.LoginHistoryService
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Role
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.core.support.response.ApiResponse
import io.soo.springboot.storage.db.core.LoginHistory
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/auth/admin")
class AdminAuthController(
    private val loginHistoryService: LoginHistoryService,
    private val userIdResolver: UserIdResolver,
    private val adminUserBlockService: UserBlock,
    private val adminUserManagementService: UserAdmin,
) {

    @GetMapping("/login-history",produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getLoginHistory(
        authentication: Authentication,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: LocalDateTime?,
        req: HttpServletRequest,
    ): ApiResponse<Page<LoginHistory>> {
        val userId = userIdResolver.resolve(authentication)
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))

        val result = if (startDate != null && endDate != null) {
            loginHistoryService.findByUserIdAndDateRange(userId, startDate, endDate, pageable)
        } else {
            loginHistoryService.findByUserId(userId, pageable)
        }

        return ApiResponse.success(req = req, data = result)
    }

    @PostMapping("/users/{userId}/block", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun block(
        authentication: Authentication,
        @PathVariable userId: Long,
        @RequestBody @Valid body: AdminUserBlockRequest,
        req: HttpServletRequest,
    ): ApiResponse<AdminUserBlockResponse> {
        val adminUserId = userIdResolver.resolve(authentication)
        val blocked = adminUserBlockService.block(
            targetUserId = userId,
            adminUserId = adminUserId,
            reason = body.reason,
        )

        return ApiResponse.success(req = req, data = AdminUserBlockResponse.from(blocked))
    }

    @PostMapping("/users/{userId}/unblock", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun unblock(
        authentication: Authentication,
        @PathVariable userId: Long,
        req: HttpServletRequest,
    ): ApiResponse<AdminUserBlockResponse> {
        val adminUserId = userIdResolver.resolve(authentication)
        val unblocked = adminUserBlockService.unblock(
            targetUserId = userId,
            adminUserId = adminUserId,
        )

        return ApiResponse.success(req = req, data = AdminUserBlockResponse.from(unblocked))
    }

    @GetMapping("/users/{userId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun get(
        @PathVariable userId: Long,
        req: HttpServletRequest,
    ): ApiResponse<AdminUserDetailResponse> {
        val found = adminUserManagementService.get(userId)
        return ApiResponse.success(req = req, data = AdminUserDetailResponse.from(found))
    }

    @GetMapping("/users", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) userStatus: UserStatus?,
        @RequestParam(required = false) role: Role?,
        @RequestParam(required = false) authProvider: AuthProvider?,
        req: HttpServletRequest,
    ): ApiResponse<Page<AdminUserSummaryResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        val result = adminUserManagementService.list(
            keyword = keyword,
            userStatus = userStatus,
            role = role,
            authProvider = authProvider,
            pageable = pageable,
        ).map { AdminUserSummaryResponse.from(it) }
        return ApiResponse.success(req = req, data = result)
    }

    @PatchMapping("/users/{userId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun update(
        authentication: Authentication,
        @PathVariable userId: Long,
        @RequestBody @Valid body: AdminUserUpdateRequest,
        req: HttpServletRequest,
    ): ApiResponse<AdminUserDetailResponse> {
        val adminUserId = userIdResolver.resolve(authentication)
        val updated = adminUserManagementService.update(
            userId = userId,
            command = UserUpdateCmd(
                email = body.email,
                phoneNumber = body.phoneNumber,
                name = body.name,
                nickname = body.nickname,
                gender = body.gender,
                locale = body.locale,
                birthyear = body.birthyear,
                birthday = body.birthday,
                profileImageUrl = body.profileImageUrl,
                thumbnailImageUrl = body.thumbnailImageUrl,
                role = body.role,
                userStatus = body.userStatus,
                blocked = body.blocked,
                blockedReason = body.blockedReason,
                emailVerified = body.emailVerified,
                phoneVerified = body.phoneVerified,
            ),
            adminUserId = adminUserId,
        )
        return ApiResponse.success(req = req, data = AdminUserDetailResponse.from(updated))
    }

    @PostMapping("/users/{userId}/delete", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun delete(
        authentication: Authentication,
        @PathVariable userId: Long,
        @RequestBody(required = false) @Valid body: AdminUserDeleteRequest?,
        req: HttpServletRequest,
    ): ApiResponse<AdminUserBlockResponse> {
        val adminUserId = userIdResolver.resolve(authentication)
        val deleted = adminUserManagementService.delete(
            userId = userId,
            reason = body?.reason,
            adminUserId = adminUserId,
        )
        return ApiResponse.success(req = req, data = AdminUserBlockResponse.from(deleted))
    }

    @PostMapping("/users/{userId}/password/reset", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun resetUserPassword(
        @PathVariable userId: Long,
        @RequestBody @Valid body: AdminUserPasswordResetRequest,
        req: HttpServletRequest,
    ): ApiResponse<AdminUserDetailResponse> {
        val user = adminUserManagementService.resetPw(userId = userId, newPassword = body.newPassword)
        return ApiResponse.success(req = req, data = AdminUserDetailResponse.from(user))
    }

    @PostMapping("/users/{userId}/tokens/revoke", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun revokeTokens(
        @PathVariable userId: Long,
        req: HttpServletRequest,
    ): ApiResponse<AdminUserDetailResponse> {
        val user = adminUserManagementService.revokeTokens(userId = userId)
        return ApiResponse.success(req = req, data = AdminUserDetailResponse.from(user))
    }

    @GetMapping("/users/blocked", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getBlockedUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        req: HttpServletRequest,
    ): ApiResponse<Page<AdminUserBlockResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "blockedAt"))
        val result = adminUserBlockService.blocked(pageable).map { AdminUserBlockResponse.from(it) }
        return ApiResponse.success(req = req, data = result)
    }

    @GetMapping("/users/deleted", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getSoftDeletedUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        req: HttpServletRequest,
    ): ApiResponse<Page<AdminUserBlockResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "deletedAt"))
        val result = adminUserBlockService.deleted(pageable).map { AdminUserBlockResponse.from(it) }
        return ApiResponse.success(req = req, data = result)
    }

    @GetMapping("/users/{userId}/status-audits", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getStatusAuditLogs(
        @PathVariable userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        req: HttpServletRequest,
    ): ApiResponse<Page<UserStatusAuditLogResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "actionAt"))
        val result = adminUserBlockService.logs(userId, pageable).map { UserStatusAuditLogResponse.from(it) }
        return ApiResponse.success(req = req, data = result)
    }
}
