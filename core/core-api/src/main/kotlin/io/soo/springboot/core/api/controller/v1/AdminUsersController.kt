package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.AdminUserDeleteRequest
import io.soo.springboot.core.api.controller.v1.request.AdminUserPasswordResetRequest
import io.soo.springboot.core.api.controller.v1.request.AdminUserUpdateRequest
import io.soo.springboot.core.api.controller.v1.response.AdminUserBlockResponse
import io.soo.springboot.core.api.controller.v1.response.AdminUserDetailResponse
import io.soo.springboot.core.api.controller.v1.response.AdminUserSummaryResponse
import io.soo.springboot.core.api.security.auth.UserIdResolver
import io.soo.springboot.core.domain.admin.UserAdmin
import io.soo.springboot.core.domain.admin.UserUpdateCmd
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Role
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/api/v1/auth/admin")
class AdminUsersController(
    private val adminUserManagementService: UserAdmin,
    private val userIdResolver: UserIdResolver,
) {
    @GetMapping("/users/{userId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getUser(
        @PathVariable userId: Long,
        req: HttpServletRequest,
    ): ApiResponse<AdminUserDetailResponse> {
        val found = adminUserManagementService.get(userId)
        return ApiResponse.success(req = req, data = AdminUserDetailResponse.from(found))
    }

    @GetMapping("/users", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun listUsers(
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
    fun updateUser(
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

    @PostMapping("/users/{userId}/password/reset", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun resetUserPassword(
        @PathVariable userId: Long,
        @RequestBody @Valid body: AdminUserPasswordResetRequest,
        req: HttpServletRequest,
    ): ApiResponse<AdminUserDetailResponse> {
        val user = adminUserManagementService.resetPw(userId = userId, newPassword = body.newPassword)
        return ApiResponse.success(req = req, data = AdminUserDetailResponse.from(user))
    }

    @PostMapping("/users/{userId}/delete", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun softDeleteUser(
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

    @PostMapping("/users/{userId}/tokens/revoke", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun revokeUserTokens(
        @PathVariable userId: Long,
        req: HttpServletRequest,
    ): ApiResponse<AdminUserDetailResponse> {
        val user = adminUserManagementService.revokeTokens(userId = userId)
        return ApiResponse.success(req = req, data = AdminUserDetailResponse.from(user))
    }

}
