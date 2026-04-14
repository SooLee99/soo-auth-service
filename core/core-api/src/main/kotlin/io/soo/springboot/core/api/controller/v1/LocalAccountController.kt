package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.RefreshRequest
import io.soo.springboot.core.api.controller.v1.request.WithdrawRequest
import io.soo.springboot.core.api.controller.v1.response.AuthSessionStatusResponse
import io.soo.springboot.core.api.controller.v1.response.LogoutRequest
import io.soo.springboot.core.api.security.userdetails.UserPrincipal
import io.soo.springboot.core.api.security.token.AuthTokenManager
import io.soo.springboot.core.domain.local.LocalAccountService
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth/local")
class LocalAccountController(
    private val localAccountService: LocalAccountService,
    private val authTokenManager: AuthTokenManager,
) {
    @GetMapping("/session", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun sessionStatus(
        @AuthenticationPrincipal principal: Any?,
        req: HttpServletRequest,
    ): ApiResponse<AuthSessionStatusResponse> {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication is AnonymousAuthenticationToken) {
            return ApiResponse.success(
                req = req,
                data = AuthSessionStatusResponse(authenticated = false),
            )
        }

        val jwt = principal as? Jwt
        if (jwt != null) {
            val userId = (jwt.claims["uid"] as? Number)?.toLong()
            return ApiResponse.success(
                req = req,
                data = AuthSessionStatusResponse(authenticated = true, userId = userId, subject = jwt.subject),
            )
        }

        if (principal is UserPrincipal) {
            return ApiResponse.success(
                req = req,
                data = AuthSessionStatusResponse(
                    authenticated = true,
                    userId = principal.userId,
                    subject = principal.username,
                ),
            )
        }

        if (principal is UserDetails) {
            return ApiResponse.success(
                req = req,
                data = AuthSessionStatusResponse(authenticated = true, subject = principal.username),
            )
        }

        if (principal == null || authentication == null) {
            return ApiResponse.success(
                req = req,
                data = AuthSessionStatusResponse(authenticated = false),
            )
        }

        return ApiResponse.success(
            req = req,
            data = AuthSessionStatusResponse(authenticated = true),
        )
    }

    @PostMapping("/token/refresh", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun refreshTokens(
        @RequestHeader("X-Device-Id") deviceId: String,
        @RequestBody request: RefreshRequest,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        val issued = authTokenManager.refresh(request.refreshToken, deviceId)
        return ApiResponse.success(req = req, data = issued)
    }

    @PostMapping("/logout", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun logoutUser(
        @RequestHeader("X-Device-Id") deviceId: String,
        @AuthenticationPrincipal jwt: Jwt?,
        @RequestBody(required = false) body: LogoutRequest?,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        val principalJwt = jwt ?: throw CoreException(ErrorType.UNAUTHORIZED, "authenticated jwt is required")

        localAccountService.logout(
            jwt = principalJwt,
            deviceId = deviceId,
            refreshToken = body?.refreshToken,
            logoutAll = body?.logoutAll ?: false,
        )

        return ApiResponse.success(req = req, data = mapOf("result" to "OK"))
    }

    @PostMapping("/withdraw", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun withdrawUser(
        @AuthenticationPrincipal jwt: Jwt?,
        @RequestBody(required = false) @Valid body: WithdrawRequest?,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        val principalJwt = jwt ?: throw CoreException(ErrorType.UNAUTHORIZED, "authenticated jwt is required")
        val userId = (principalJwt.claims["uid"] as? Number)?.toLong()
            ?: throw CoreException(ErrorType.UNAUTHORIZED, "uid claim is required")

        localAccountService.softDeleteUser(userId = userId, reason = body?.reason)
        return ApiResponse.success(req = req, data = mapOf("result" to "OK"))
    }
}
