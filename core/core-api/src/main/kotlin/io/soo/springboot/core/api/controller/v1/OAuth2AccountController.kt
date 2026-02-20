package io.soo.springboot.core.api.controller.v1

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.security.core.Authentication
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping

import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.core.support.response.ApiResponse
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.api.controller.v1.response.LoginSuccessResponse

import io.soo.springboot.core.domain.UserIdResolver
import io.soo.springboot.core.domain.token.JsonWebTokenService
import io.soo.springboot.core.domain.token.UserPrincipal
import io.soo.springboot.core.domain.token.UserPrincipalLoader
import io.soo.springboot.core.enums.AuthProvider


@RestController
@RequestMapping("/api/v1/auth/oauth2")
class OAuth2AccountController (
    private val jsonWebTokenService: JsonWebTokenService,
    private val userIdResolver: UserIdResolver,
    private val userPrincipalLoader: UserPrincipalLoader,
) {

    @GetMapping("/{provider}/authorize-url")
    fun authorizeUrl(
        @PathVariable provider: String,
        @RequestParam(required = false) returnUrl: String?,
        @RequestHeader(name = "X-Device-Id", required = false) deviceId: String?,
        session: HttpSession,
        req: HttpServletRequest,
    ): ApiResponse<out String> {
        if (returnUrl != null && !returnUrl.startsWith("/")) {
            return ApiResponse.error(
                type = ErrorType.INVALID_REQUEST,
                message = "returnUrl은 상대경로만 허용합니다.",
                req = req
            )
        }

        session.setAttribute("RETURN_URL", returnUrl)
        session.setAttribute("DEVICE_ID", deviceId)

        val authorizePath = "/oauth2/authorization/$provider"
        return ApiResponse.success(req = req, data = authorizePath)
    }

    /**
     * ✅ 로그인 성공 응답 (JWT 발급)
     * - payload는 LoginSuccessResponse.Data / Token / User 사용
     */
    @PostMapping("/login/success", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun loginSuccess(
        req: HttpServletRequest,
        authentication: Authentication,
        @RequestHeader("X-Device-Id", required = true) deviceId: String,
    ): ApiResponse<LoginSuccessResponse.Data> {

        val normalizedDeviceId = deviceId.trim()
        if (normalizedDeviceId.isBlank()) {
            throw CoreException(
                ErrorType.INVALID_REQUEST_BODY,
                data = mapOf("deviceId" to "blank")
            )
        }

        // 1) userId
        val userId = userIdResolver.resolve(authentication)

        // 2) user principal
        val principal: UserPrincipal = userPrincipalLoader.loadByUserId(userId)

        val provider: AuthProvider = principal.provider

        // 3) JWT 발급 (✅ deviceId/provider 포함)
        val issued = jsonWebTokenService.issue(authentication, userId, normalizedDeviceId, provider)

        val roles = principal.authorities
            .map { it.authority }
            .distinct()
            .sorted()

        val payload = LoginSuccessResponse.Data(
            token = LoginSuccessResponse.Token(
                accessToken = issued.accessToken,
                expiresIn = issued.accessExpiresInSec,
                refreshToken = issued.refreshToken,
                refreshExpiresIn = issued.refreshExpiresInSec,
            ),
            user = LoginSuccessResponse.User(
                id = userId,
                provider = provider.name,
                email = principal.email ?: authentication.name,
                roles = roles,
            )
        )

        return ApiResponse.success(req, data = payload)
    }
}