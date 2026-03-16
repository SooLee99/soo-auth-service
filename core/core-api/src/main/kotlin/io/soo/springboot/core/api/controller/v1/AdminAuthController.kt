package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.AdminUserBlockRequest
import io.soo.springboot.core.api.controller.v1.response.AdminUserBlockResponse
import io.soo.springboot.core.api.controller.v1.response.UserStatusAuditLogResponse
import io.soo.springboot.core.api.security.auth.UserIdResolver
import io.soo.springboot.core.domain.AdminUserBlockService
import io.soo.springboot.core.domain.LoginHistoryService
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
    private val adminUserBlockService: AdminUserBlockService,
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
    fun blockUser(
        authentication: Authentication,
        @PathVariable userId: Long,
        @RequestBody @Valid body: AdminUserBlockRequest,
        req: HttpServletRequest,
    ): ApiResponse<AdminUserBlockResponse> {
        val adminUserId = userIdResolver.resolve(authentication)
        val blocked = adminUserBlockService.blockUser(
            targetUserId = userId,
            adminUserId = adminUserId,
            reason = body.reason,
        )

        return ApiResponse.success(req = req, data = AdminUserBlockResponse.from(blocked))
    }

    @PostMapping("/users/{userId}/unblock", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun unblockUser(
        authentication: Authentication,
        @PathVariable userId: Long,
        req: HttpServletRequest,
    ): ApiResponse<AdminUserBlockResponse> {
        val adminUserId = userIdResolver.resolve(authentication)
        val unblocked = adminUserBlockService.unblockUser(
            targetUserId = userId,
            adminUserId = adminUserId,
        )

        return ApiResponse.success(req = req, data = AdminUserBlockResponse.from(unblocked))
    }

    @GetMapping("/users/blocked", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getBlockedUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        req: HttpServletRequest,
    ): ApiResponse<Page<AdminUserBlockResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "blockedAt"))
        val result = adminUserBlockService.findBlockedUsers(pageable).map { AdminUserBlockResponse.from(it) }
        return ApiResponse.success(req = req, data = result)
    }

    @GetMapping("/users/deleted", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getSoftDeletedUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        req: HttpServletRequest,
    ): ApiResponse<Page<AdminUserBlockResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "deletedAt"))
        val result = adminUserBlockService.findSoftDeletedUsers(pageable).map { AdminUserBlockResponse.from(it) }
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
        val result = adminUserBlockService.findStatusAuditLogs(userId, pageable).map { UserStatusAuditLogResponse.from(it) }
        return ApiResponse.success(req = req, data = result)
    }
}
