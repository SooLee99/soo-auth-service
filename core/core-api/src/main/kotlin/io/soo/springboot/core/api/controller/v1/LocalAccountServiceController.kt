package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.LoginRequest
import io.soo.springboot.core.api.controller.v1.request.RefreshRequest
import io.soo.springboot.core.api.controller.v1.request.SignUpRequest
import io.soo.springboot.core.api.controller.v1.request.PhoneSignUpRequest
import io.soo.springboot.core.api.controller.v1.request.WithdrawRequest
import io.soo.springboot.core.api.controller.v1.response.LogoutRequest
import io.soo.springboot.core.api.security.auth.UserIdResolver
import io.soo.springboot.core.api.security.token.AuthTokenManager
import io.soo.springboot.core.api.security.userdetails.UserPrincipal
import io.soo.springboot.core.domain.admin.AppResolver
import io.soo.springboot.core.domain.admin.AppAccess
import io.soo.springboot.core.domain.LoginHistoryService
import io.soo.springboot.core.domain.local.LocalAccount
import io.soo.springboot.core.domain.local.LocalSignUpCmd
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.LoginType
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/services/{serviceCode}/auth/local")
class LocalAccountServiceController(
    private val localAccountService: LocalAccount,
    private val authTokenManager: AuthTokenManager,
    private val serviceContextResolver: AppResolver,
    private val serviceMembershipAccessService: AppAccess,
    private val authenticationManager: AuthenticationManager,
    private val userIdResolver: UserIdResolver,
    private val loginHistoryService: LoginHistoryService,
) {
    @PostMapping("/login", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun login(
        @PathVariable serviceCode: String,
        @RequestHeader("X-Device-Id") deviceId: String,
        @RequestBody @Valid request: LoginRequest,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        val resolvedService = serviceContextResolver.active(serviceCode)
        val authRequest = UsernamePasswordAuthenticationToken(
            request.email.trim().lowercase(),
            request.password.trim(),
        )

        val authentication = try {
            authenticationManager.authenticate(authRequest)
        } catch (_: AuthenticationException) {
            throw CoreException(ErrorType.LOGIN_BAD_CREDENTIALS)
        }

        val userId = userIdResolver.resolve(authentication)
        serviceMembershipAccessService.member(resolvedService.id, userId)

        val tokens = authTokenManager.issue(
            authentication = authentication,
            userId = userId,
            deviceId = deviceId,
            provider = AuthProvider.LOCAL,
            serviceId = resolvedService.id,
        )

        val principal = authentication.principal as? UserPrincipal
        loginHistoryService.recordLoginSuccess(
            userId = userId,
            userEmail = principal?.email ?: request.email.trim().lowercase(),
            loginType = LoginType.LOCAL,
            ipAddress = req.remoteAddr,
            userAgent = req.getHeader("User-Agent"),
            deviceId = deviceId,
        )

        return ApiResponse.success(
            req = req,
            data = mapOf("serviceCode" to resolvedService.serviceCode, "tokens" to tokens),
        )
    }

    @PostMapping("/signup", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun signup(
        @PathVariable serviceCode: String,
        @RequestBody @Valid request: SignUpRequest,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        val resolvedService = serviceContextResolver.active(serviceCode)
        val signedUp = localAccountService.signup(
            LocalSignUpCmd(
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
            )
        )
        serviceMembershipAccessService.join(resolvedService.id, signedUp.id)

        return ApiResponse.success(req = req, data = mapOf("result" to "OK", "serviceCode" to resolvedService.serviceCode))
    }

    @PostMapping("/signup/phone", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun signupPhone(
        @PathVariable serviceCode: String,
        @RequestBody @Valid request: PhoneSignUpRequest,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        val resolvedService = serviceContextResolver.active(serviceCode)
        val signedUp = localAccountService.signupPhone(
            phoneNumber = request.phoneNumber,
            phoneVerificationToken = request.phoneVerificationToken,
        )
        serviceMembershipAccessService.join(resolvedService.id, signedUp.id)

        return ApiResponse.success(req = req, data = mapOf("result" to "OK", "serviceCode" to resolvedService.serviceCode))
    }

    @PostMapping("/token/refresh", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun refresh(
        @PathVariable serviceCode: String,
        @RequestHeader("X-Device-Id") deviceId: String,
        @RequestBody request: RefreshRequest,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        val resolvedService = serviceContextResolver.active(serviceCode)
        val issued = authTokenManager.refresh(
            oldRefreshToken = request.refreshToken,
            deviceId = deviceId,
            expectedServiceId = resolvedService.id,
        )
        return ApiResponse.success(req = req, data = mapOf("serviceCode" to resolvedService.serviceCode, "tokens" to issued))
    }

    @PostMapping("/logout", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun logout(
        @PathVariable serviceCode: String,
        @RequestHeader("X-Device-Id") deviceId: String,
        @AuthenticationPrincipal jwt: Jwt?,
        @RequestBody(required = false) body: LogoutRequest?,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        val resolvedService = serviceContextResolver.active(serviceCode)
        val principalJwt = jwt ?: throw CoreException(ErrorType.UNAUTHORIZED, "authenticated jwt is required")
        val userId = (principalJwt.claims["uid"] as? Number)?.toLong()
            ?: throw CoreException(ErrorType.UNAUTHORIZED, "uid claim is required")
        serviceMembershipAccessService.scope(principalJwt, resolvedService.id)
        serviceMembershipAccessService.member(resolvedService.id, userId)

        authTokenManager.invalidateTokens(
            jwt = principalJwt,
            deviceId = deviceId,
            refreshToken = body?.refreshToken,
            logoutAll = body?.logoutAll ?: false,
            serviceIdScope = resolvedService.id,
        )

        return ApiResponse.success(req = req, data = mapOf("result" to "OK", "serviceCode" to resolvedService.serviceCode))
    }

    @PostMapping("/withdraw", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun withdraw(
        @PathVariable serviceCode: String,
        @AuthenticationPrincipal jwt: Jwt?,
        @RequestBody(required = false) @Valid body: WithdrawRequest?,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        val resolvedService = serviceContextResolver.active(serviceCode)
        val principalJwt = jwt ?: throw CoreException(ErrorType.UNAUTHORIZED, "authenticated jwt is required")
        serviceMembershipAccessService.scope(principalJwt, resolvedService.id)
        val userId = (principalJwt.claims["uid"] as? Number)?.toLong()
            ?: throw CoreException(ErrorType.UNAUTHORIZED, "uid claim is required")

        serviceMembershipAccessService.leave(resolvedService.id, userId, body?.reason)
        authTokenManager.invalidateTokens(
            jwt = principalJwt,
            deviceId = "",
            refreshToken = null,
            logoutAll = true,
            serviceIdScope = resolvedService.id,
        )
        return ApiResponse.success(req = req, data = mapOf("result" to "OK", "serviceCode" to resolvedService.serviceCode))
    }
}
