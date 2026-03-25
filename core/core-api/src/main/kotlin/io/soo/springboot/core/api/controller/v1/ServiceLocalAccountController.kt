package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.RefreshRequest
import io.soo.springboot.core.api.controller.v1.request.SignUpRequest
import io.soo.springboot.core.api.controller.v1.request.PhoneSignUpRequest
import io.soo.springboot.core.api.controller.v1.request.WithdrawRequest
import io.soo.springboot.core.api.controller.v1.response.LogoutRequest
import io.soo.springboot.core.api.security.token.AuthTokenManager
import io.soo.springboot.core.domain.admin.ServiceContextResolver
import io.soo.springboot.core.domain.local.LocalAccountService
import io.soo.springboot.core.domain.local.LocalSignUpCommand
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/services/{serviceCode}/auth/local")
class ServiceLocalAccountController(
    private val localAccountService: LocalAccountService,
    private val authTokenManager: AuthTokenManager,
    private val serviceContextResolver: ServiceContextResolver,
) {
    @PostMapping("/signup", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun signUp(
        @PathVariable serviceCode: String,
        @RequestBody @Valid request: SignUpRequest,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        val resolvedService = serviceContextResolver.resolveActive(serviceCode)
        // TODO(multi-service): membership create
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
            )
        )

        return ApiResponse.success(req = req, data = mapOf("result" to "OK", "serviceCode" to resolvedService.serviceCode))
    }

    @PostMapping("/signup/phone", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun signUpByPhone(
        @PathVariable serviceCode: String,
        @RequestBody @Valid request: PhoneSignUpRequest,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        val resolvedService = serviceContextResolver.resolveActive(serviceCode)
        // TODO(multi-service): membership create
        localAccountService.signUpByPhone(
            phoneNumber = request.phoneNumber,
            phoneVerificationToken = request.phoneVerificationToken,
        )

        return ApiResponse.success(req = req, data = mapOf("result" to "OK", "serviceCode" to resolvedService.serviceCode))
    }

    @PostMapping("/token/refresh", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun refresh(
        @PathVariable serviceCode: String,
        @RequestHeader("X-Device-Id") deviceId: String,
        @RequestBody request: RefreshRequest,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        val resolvedService = serviceContextResolver.resolveActive(serviceCode)
        // TODO(multi-service): refresh token service scope check
        val issued = authTokenManager.refresh(request.refreshToken, deviceId)
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
        val resolvedService = serviceContextResolver.resolveActive(serviceCode)
        val principalJwt = jwt ?: throw CoreException(ErrorType.UNAUTHORIZED, "authenticated jwt is required")

        // TODO(multi-service): logout by service scope
        localAccountService.logout(
            jwt = principalJwt,
            deviceId = deviceId,
            refreshToken = body?.refreshToken,
            logoutAll = body?.logoutAll ?: false,
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
        val resolvedService = serviceContextResolver.resolveActive(serviceCode)
        val principalJwt = jwt ?: throw CoreException(ErrorType.UNAUTHORIZED, "authenticated jwt is required")
        val userId = (principalJwt.claims["uid"] as? Number)?.toLong()
            ?: throw CoreException(ErrorType.UNAUTHORIZED, "uid claim is required")

        // TODO(multi-service): split service-withdraw and global-withdraw
        localAccountService.softDelete(userId = userId, reason = body?.reason)
        return ApiResponse.success(req = req, data = mapOf("result" to "OK", "serviceCode" to resolvedService.serviceCode))
    }
}
