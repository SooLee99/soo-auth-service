package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.AdminUserBlockRequest
import io.soo.springboot.core.api.controller.v1.response.AdminUserBlockResponse
import io.soo.springboot.core.api.controller.v1.response.UserStatusAuditLogResponse
import io.soo.springboot.core.api.security.auth.UserIdResolver
import io.soo.springboot.core.domain.admin.UserBlockService
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth/admin")
class AdminUserStatusController(
    private val userIdResolver: UserIdResolver,
    private val adminUserBlockServiceService: UserBlockService,
) {

    @GetMapping("/users/blocked", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun listBlockedUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        req: HttpServletRequest,
    ): ApiResponse<Page<AdminUserBlockResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "blockedAt"))
        val result = adminUserBlockServiceService.blocked(pageable).map { AdminUserBlockResponse.from(it) }
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
        val blocked = adminUserBlockServiceService.block(
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
        val unblocked = adminUserBlockServiceService.unblock(
            targetUserId = userId,
            adminUserId = adminUserId,
        )

        return ApiResponse.success(req = req, data = AdminUserBlockResponse.from(unblocked))
    }

    @GetMapping("/users/deleted", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun listDeletedUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        req: HttpServletRequest,
    ): ApiResponse<Page<AdminUserBlockResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "deletedAt"))
        val result = adminUserBlockServiceService.deleted(pageable).map { AdminUserBlockResponse.from(it) }
        return ApiResponse.success(req = req, data = result)
    }

    @GetMapping("/users/{userId}/status-audits", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun listStatusAudits(
        @PathVariable userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        req: HttpServletRequest,
    ): ApiResponse<Page<UserStatusAuditLogResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "actionAt"))
        val result = adminUserBlockServiceService.logs(userId, pageable).map { UserStatusAuditLogResponse.from(it) }
        return ApiResponse.success(req = req, data = result)
    }
}
