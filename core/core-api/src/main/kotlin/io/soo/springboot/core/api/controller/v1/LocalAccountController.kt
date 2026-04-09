package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.PhoneLoginRequest
import io.soo.springboot.core.api.controller.v1.request.PhoneSignUpRequest
import io.soo.springboot.core.api.controller.v1.request.RefreshRequest
import io.soo.springboot.core.api.controller.v1.request.SignUpRequest
import io.soo.springboot.core.api.controller.v1.request.WithdrawRequest
import io.soo.springboot.core.api.controller.v1.response.LogoutRequest
import io.soo.springboot.core.api.security.token.AuthTokenManager
import io.soo.springboot.core.domain.local.LocalAccountService
import io.soo.springboot.core.domain.local.LocalSignUpCommand
import io.soo.springboot.core.domain.phone.account.LocalPhoneAccountService
import io.soo.springboot.core.domain.phone.login.LocalPhoneLoginCommand
import io.soo.springboot.core.domain.phone.login.LocalPhoneLoginService
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth/local")
class LocalAccountController(
    private val localAccountService: LocalAccountService,
    private val localPhoneAccountService: LocalPhoneAccountService,
    private val localPhoneLoginService: LocalPhoneLoginService,
    private val authTokenManager: AuthTokenManager,
) {
    @PostMapping("/signup")
    fun signUp(@RequestBody @Valid request: SignUpRequest) {
        localAccountService.signUp(
            LocalSignUpCommand(
                email = request.email,
                password = request.password,
                phoneNumber = request.phoneNumber,
                phoneVerificationToken = request.phoneVerificationToken,
                gender = request.gender,
                locale = request.locale,
                nickname = request.nickname,
                name = request.name,
                profileImageUrl = request.profileImageUrl,
                thumbnailImageUrl = request.thumbnailImageUrl,
                birthyear = request.birthyear,
                birthday = request.birthday,
            ),
        )
    }

    @PostMapping("/signup/phone")
    fun signUpWithPhone(@RequestBody @Valid request: PhoneSignUpRequest) {
        localPhoneAccountService.signUpWithPhone(
            phoneNumber = request.phoneNumber,
            phoneVerificationToken = request.phoneVerificationToken,
        )
    }

    @PostMapping("/login/phone", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun loginWithPhone(
        @RequestHeader("X-Device-Id") deviceId: String,
        @RequestBody @Valid request: PhoneLoginRequest,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        val tokens = localPhoneLoginService.login(
            LocalPhoneLoginCommand(
                phoneNumber = request.phoneNumber,
                phoneVerificationToken = request.phoneVerificationToken,
                deviceId = deviceId,
                ipAddress = req.remoteAddr,
                userAgent = req.getHeader("User-Agent"),
            ),
        )

        return ApiResponse.success(req = req, data = tokens)
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
