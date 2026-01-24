package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.AdminSuspendUserRequest
import io.soo.springboot.core.api.controller.v1.request.AdminUpdateUserRequest
import io.soo.springboot.core.api.controller.v1.response.AdminUserDetailResponse
import io.soo.springboot.core.api.controller.v1.response.AdminUserSummaryResponse
import io.soo.springboot.core.domain.AdminUserCommandService
import io.soo.springboot.core.domain.AdminUserQueryService
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.support.response.ApiResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUserController(
    private val userQuery: AdminUserQueryService,
    private val userCommand: AdminUserCommandService,
) {

    /**
     * ✅ 유저 목록(검색/필터 지원)
     * GET /api/v1/admin/users?page=0&size=20&q=...&email=...&nickname=...&provider=...&suspended=true
     */
    @GetMapping
    fun listUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) email: String?,
        @RequestParam(required = false) nickname: String?,
        @RequestParam(required = false) provider: AuthProvider?,
        @RequestParam(required = false) suspended: Boolean?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        createdFrom: LocalDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        createdTo: LocalDateTime?,
    ): ApiResponse<List<AdminUserSummaryResponse>> {
        val r = userQuery.listUsers(
            page = page,
            size = size,
            q = q,
            email = email,
            nickname = nickname,
            provider = provider,
            suspended = suspended,
            createdFrom = createdFrom,
            createdTo = createdTo,
        )
        return ApiResponse.success(r.items, r.page)
    }

    /**
     * ✅ 유저 상세
     * GET /api/v1/admin/users/{userId}
     */
    @GetMapping("/{userId}")
    fun getUserDetail(@PathVariable userId: Long): ApiResponse<AdminUserDetailResponse> =
        ApiResponse.success(userQuery.getUserDetail(userId))

    /**
     * ✅ 유저 정지
     * POST /api/v1/admin/users/{userId}/suspend
     */
    @PostMapping("/{userId}/suspend")
    fun suspendUser(
        @PathVariable userId: Long,
        @RequestBody req: AdminSuspendUserRequest,
    ): ApiResponse<Unit> {
        userCommand.suspendUser(userId, req.reason, req.until)
        return ApiResponse.success(Unit)
    }

    /**
     * ✅ 유저 정지 해제
     * POST /api/v1/admin/users/{userId}/unsuspend
     */
    @PostMapping("/{userId}/unsuspend")
    fun unsuspendUser(
        @PathVariable userId: Long,
    ): ApiResponse<Unit> {
        userCommand.unsuspendUser(userId)
        return ApiResponse.success(Unit)
    }

    /**
     * ✅ 유저 프로필 부분 수정
     * PATCH /api/v1/admin/users/{userId}
     */
    @PatchMapping("/{userId}")
    fun updateUser(
        @PathVariable userId: Long,
        @RequestBody req: AdminUpdateUserRequest,
    ): ApiResponse<Unit> {
        userCommand.updateUserProfile(userId, req)
        return ApiResponse.success(Unit)
    }
}
