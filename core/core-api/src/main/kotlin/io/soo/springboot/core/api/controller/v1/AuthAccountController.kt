package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.IdLoginRequest
import io.soo.springboot.core.api.controller.v1.request.IdSignUpRequest
import io.soo.springboot.core.api.controller.v1.request.PhoneLoginRequest
import io.soo.springboot.core.api.controller.v1.request.PhoneSignUpRequest
import io.soo.springboot.core.api.controller.v1.request.RefreshRequest
import io.soo.springboot.core.api.controller.v1.request.SignUpRequest
import io.soo.springboot.core.api.security.token.AuthTokenManager
import io.soo.springboot.core.domain.id.IdAccountService
import io.soo.springboot.core.domain.id.IdLoginCommand
import io.soo.springboot.core.domain.id.IdLoginService
import io.soo.springboot.core.domain.id.IdSignUpCommand
import io.soo.springboot.core.domain.local.LocalAccountService
import io.soo.springboot.core.domain.local.LocalSignUpCommand
import io.soo.springboot.core.domain.phone.account.LocalPhoneAccountService
import io.soo.springboot.core.domain.phone.login.LocalPhoneLoginCommand
import io.soo.springboot.core.domain.phone.login.LocalPhoneLoginService
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthAccountController(
    private val localAccountService: LocalAccountService,
    private val localPhoneAccountService: LocalPhoneAccountService,
    private val localPhoneLoginService: LocalPhoneLoginService,
    private val idAccountService: IdAccountService,
    private val idLoginService: IdLoginService,
    private val authTokenManager: AuthTokenManager,
) {
    @PostMapping("/email/signup")
    fun signUpWithEmail(@RequestBody @Valid request: SignUpRequest) {
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

    @PostMapping("/phone/signup")
    fun signUpWithPhone(@RequestBody @Valid request: PhoneSignUpRequest) {
        localPhoneAccountService.signUpWithPhone(request.phoneNumber, request.phoneVerificationToken)
    }

    @PostMapping("/phone/login", produces = [MediaType.APPLICATION_JSON_VALUE])
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

    @PostMapping("/id/signup")
    fun signUpWithId(@RequestBody @Valid request: IdSignUpRequest) {
        idAccountService.signUp(
            IdSignUpCommand(
                loginId = request.loginId,
                password = request.password,
                phoneNumber = request.phoneNumber,
                phoneVerificationToken = request.phoneVerificationToken,
            ),
        )
    }

    @PostMapping("/id/login", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun loginWithId(
        @RequestHeader("X-Device-Id") deviceId: String,
        @RequestBody @Valid request: IdLoginRequest,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        val tokens = idLoginService.login(
            IdLoginCommand(
                loginId = request.loginId,
                password = request.password,
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
}
